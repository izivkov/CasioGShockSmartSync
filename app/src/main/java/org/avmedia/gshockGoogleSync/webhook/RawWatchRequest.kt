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
        if (waiting) {
            synchronized(buffer) { buffer.add(data) }
            lastRxMillis = System.currentTimeMillis()
        } else {
            link.forward(data)
        }
    }

    override suspend fun request(code: Int, timeoutMs: Long): List<String> = mutex.withLock {
        // A LifeLog transfer is running: it needs the link, so this read does not start.
        if (!WatchExchange.mutex.tryLock()) return@withLock emptyList()
        try {
            exchange(code, timeoutMs)
        } finally {
            WatchExchange.mutex.unlock()
        }
    }

    private suspend fun exchange(code: Int, timeoutMs: Long): List<String> {
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
            link.write(code)
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
                link.setCallback(IDataReceived { data -> data?.let { link.forward(it) } })
            } catch (ignored: Throwable) {
            }
        }
        return synchronized(buffer) { buffer.toList() }
    }

    companion object {
        /** True for the answer to [code]: first byte is the code, or 0xFF followed by the code (the error answer). */
        fun answersTo(data: String, code: Int): Boolean {
            val b = WatchDecoders.bytes(data) ?: return false
            return b.firstOrNull() == code || (b.size >= 2 && b[0] == 0xFF && b[1] == code)
        }
    }
}
