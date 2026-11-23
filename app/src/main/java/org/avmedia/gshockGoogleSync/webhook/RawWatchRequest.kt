package org.avmedia.gshockGoogleSync.webhook

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.io.WatchExchange

fun interface RawRequester {
    suspend fun request(code: Int, timeoutMs: Long): List<String>
}

/** The watch link as [RawWatchRequest] needs it. A fake in the unit tests. */
interface RawLink {
    /** The notification handler now installed. */
    fun callback(): IDataReceived?
    fun setCallback(callback: IDataReceived)

    /** Hands a notification to the library, as if no request were running. */
    fun forward(data: String)
    fun write(code: Int)

    /** A request longer than one byte (the first byte is the code). */
    fun writeBytes(bytes: ByteArray) = write(bytes[0].toInt() and 0xFF)
}

internal object AndroidRawLink : RawLink {
    override fun callback(): IDataReceived? = Connection.getDataCallback()
    override fun setCallback(callback: IDataReceived) = Connection.setDataCallback(callback)
    override fun forward(data: String) {
        try {
            MessageDispatcher.onReceived(data, WatchInfo.protocol)
        } catch (t: Throwable) {
            WebhookDiagnostics.logError("Raw request: library handler failed: ${WebhookDiagnostics.oneLine(t)}")
        }
    }
    override fun write(code: Int) = IO.writeCmd(GetSetMode.GET, byteArrayOf(code.toByte()))
    override fun writeBytes(bytes: ByteArray) = IO.writeCmd(GetSetMode.GET, bytes)
}

/**
 * Sends one Casio read-request code (one byte, request characteristic 26eb002c) and returns the raw
 * notifications that answer it ("0x28 06 1D ..."), outside the library's per-model profile.
 *
 * While a request is outstanding the library's handler is replaced by one that keeps only the answers to
 * this code (first byte = the code, or the error answer 0xFF + code) and passes every other notification
 * to the library at once, so a LifeLog transfer is never starved. Afterwards the handler that was installed
 * before is put back, the same instance. One request at a time, and none while a LifeLog transfer holds the
 * [WatchExchange]: such a request returns nothing.
 */
@Singleton
class RawWatchRequest internal constructor(private val link: RawLink) : RawRequester {
    @Inject constructor() : this(AndroidRawLink)

    private val mutex = Mutex()
    @Volatile private var waiting = false
    @Volatile private var code = -1
    @Volatile private var lastRxMillis = 0L
    private val buffer = ArrayList<String>()

    private val collector = IDataReceived { data ->
        if (data == null) return@IDataReceived
        // Every notification that arrives while a read waits is logged (first bytes only), so a refused or lost answer can be seen.
        if (waiting) ConnectionKeepAlive.log("read 0x%02X rx %s".format(code, data.trim().split(' ').take(LOG_BYTES).joinToString(" ")))
        if (waiting && answersTo(data, code)) {
            synchronized(buffer) { buffer.add(data) }
            lastRxMillis = System.currentTimeMillis()
        } else {
            link.forward(data)
        }
    }

    override suspend fun request(code: Int, timeoutMs: Long): List<String> = request(byteArrayOf(code.toByte()), timeoutMs)

    /** Like [request] for a request of several bytes (for example 54 00 activity class part); answers are those of the first byte. */
    suspend fun request(bytes: ByteArray, timeoutMs: Long): List<String> = mutex.withLock {
        // A LifeLog transfer is running: it needs the link, so this read does not start.
        if (!WatchExchange.mutex.tryLock()) return@withLock emptyList()
        try {
            exchange(bytes, timeoutMs)
        } finally {
            WatchExchange.mutex.unlock()
        }
    }

    private suspend fun exchange(bytes: ByteArray, timeoutMs: Long): List<String> {
        val code = bytes[0].toInt() and 0xFF
        synchronized(buffer) { buffer.clear() }
        lastRxMillis = 0L
        this.code = code
        val before = try { link.callback() } catch (t: Throwable) { null }
        try {
            link.setCallback(collector)
        } catch (t: Throwable) {
            WebhookDiagnostics.logError("Raw request: cannot hook notifications: ${WebhookDiagnostics.oneLine(t)}")
        }
        waiting = true
        try {
            ConnectionKeepAlive.log("read tx " + bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } + " (waits up to ${timeoutMs / 1000} s)")
            if (bytes.size == 1) link.write(code) else link.writeBytes(bytes)
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                delay(100)
                val rx = lastRxMillis
                if (rx != 0L && System.currentTimeMillis() - rx > 400) break
            }
        } finally {
            waiting = false
            try {
                // The handler that was there before, not a new one: the library may have set it for its own purpose.
                link.setCallback(before ?: IDataReceived { data -> data?.let { link.forward(it) } })
            } catch (ignored: Throwable) {
            }
        }
        val answers = synchronized(buffer) { buffer.toList() }
        if (answers.isEmpty()) ConnectionKeepAlive.log("read 0x%02X: no answer within %d ms".format(code, timeoutMs))
        return answers
    }

    /** The result of a settings read: the [frame] (code first) the [accept] check took, or the plain-words [failure]. */
    class Reply(val frame: ByteArray?, val failure: String?)

    /**
     * One settings read: sends [request], waits up to [timeoutMs] (Casio waits 10 s), and returns the first answer that begins with
     * the code and that [accept] takes. An `FF` answer is a refusal and its status is named; nothing is retried.
     */
    suspend fun read(request: ByteArray, timeoutMs: Long = READ_TIMEOUT_MS, accept: (ByteArray) -> Boolean = { true }): Reply {
        val code = request[0].toInt() and 0xFF
        val answers = request(request, timeoutMs)
        for (a in answers) {
            val b = WatchDecoders.bytes(a) ?: continue
            if (b.size > 1 && b[0] == code) {
                val frame = ByteArray(b.size) { b[it].toByte() }
                if (accept(frame)) return Reply(frame, null)
            }
        }
        answers.firstNotNullOfOrNull { failureStatus(it, code) }?.let { st ->
            return Reply(null, if (st < 0) "watch refused: no status" else "watch refused: status %02X".format(st))
        }
        return Reply(null, if (answers.isEmpty()) "The watch did not answer within ${timeoutMs / 1000} s." else "The watch answered with something else.")
    }

    companion object {
        /** True for the answer to [code]: first byte is the code, or 0xFF followed by the code (the error answer). */
        fun answersTo(data: String, code: Int): Boolean {
            val b = WatchDecoders.bytes(data) ?: return false
            // Any FF notification while a read waits means that read failed (notes D2); the byte after it is the status.
            return b.firstOrNull() == code || b.firstOrNull() == 0xFF
        }

        /** Casio waits up to 10 s for the answer to a settings read. */
        const val READ_TIMEOUT_MS = 10_000L

        private const val LOG_BYTES = 8

        /** The status of an `FF` answer: `FF status`, or `FF code status` (-1 when there is none); null when [data] is not an FF answer. */
        fun failureStatus(data: String, code: Int): Int? {
            val b = WatchDecoders.bytes(data) ?: return null
            if (b.firstOrNull() != 0xFF) return null
            return when {
                b.size >= 3 && b[1] == code -> b[2]
                b.size >= 2 && b[1] != code -> b[1]
                else -> -1
            }
        }
    }
}
