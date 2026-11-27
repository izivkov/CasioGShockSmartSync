package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** "42 05 3D …" */
internal fun IntArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/** The wire as RawSettingIO needs it. The real one writes through [IO]; a fake in the unit tests. */
interface SettingLink {
    /** A read request: one byte (the code), or several (for example `54 00 01 40 00`). */
    fun sendRequest(bytes: ByteArray)

    /** A full packet, not waited for. */
    fun sendPacket(packet: IntArray)

    /** A full packet; true when the watch confirmed the write. */
    suspend fun sendChecked(bytes: ByteArray): Boolean
}

internal object AndroidSettingLink : SettingLink {
    override fun sendRequest(bytes: ByteArray) = IO.writeCmd(GetSetMode.GET, bytes)

    override fun sendPacket(packet: IntArray) = IO.writeCmd(GetSetMode.SET, Utils.byteArrayOfIntArray(packet))

    override suspend fun sendChecked(bytes: ByteArray): Boolean = IO.writeChecked(GetSetMode.SET, bytes)
}

/**
 * The one reader / writer for settings packets whose first byte is the command code (DW-H5600: 0x24, 0x39, 0x3D,
 * 0x41, 0x42, 0x45, 0x4F, 0x50, 0x51, 0x2E, 0x54 and the rest of the raw table, and raw 0x13 restores).
 *
 * Read:  the request on the read-request characteristic (GetSetMode.GET, 26eb002c): the code alone, `3D 30` for 0x3D,
 *        or several bytes. The watch answers with a notification whose first byte is the code, or `FF ...` when it refuses.
 *        [MessageDispatcher] offers every notification to [offer] first: it takes only the answer to the request that waits
 *        and passes everything else (LifeLog, CONVOY, other codes) on, so no handler is ever replaced.
 * Write: the full packet on the all-features characteristic (GetSetMode.SET, 26eb002d); [safeWriter] reads, changes
 *        only the target bytes, writes with a write result and reads back.
 *
 * One request at a time ([sendLock]), and none while a LifeLog transfer holds [WatchExchange] (it fails at once).
 * [request] (retried, shared by concurrent readers of the same request) and [read] (one try, plain-words failure)
 * are the same exchange; [modifySafe] serializes read-modify-write per code, so two quick changes cannot undo each other.
 */
object RawSettingIO {
    private const val TRIES = 3

    /** The watch answers a settings read within 10 s. */
    const val READ_TIMEOUT_MS = 10_000L

    // Seams for unit tests (a fake watch); production values below.
    @Volatile var link: SettingLink = AndroidSettingLink
    var tryTimeoutMs = 3_000L
    var retryBaseDelayMs = 400L
    var settleMs = 1_000L

    /** The result of [read]: the [frame] (code first), or the plain-words [failure]. */
    class Reply(val frame: ByteArray?, val failure: String?)

    private class Pending(val key: String, val code: Int, val accept: (IntArray) -> Boolean) {
        val deferred = CompletableDeferred<IntArray>()
    }

    private val lock = Any()
    private var current: Pending? = null
    private val sendLock = Mutex()
    private val locks = ConcurrentHashMap<Int, Mutex>()

    private class Failure(val text: String) : IllegalStateException(text)

    private class NoAnswer(code: Int) : IllegalStateException("no answer to 0x%02X".format(code))

    /** What a read of [code] sends: the code alone, except 0x3D, which is read as `3D 30` and never bare. */
    fun requestFor(code: Int): ByteArray = if (code == 0x3D) byteArrayOf(0x3D, 0x30) else byteArrayOf(code.toByte())

    /** The default check of a code read: the answer is longer than the code and, for `3D 30`, names the same sub-request. */
    private fun answersRequest(
        request: ByteArray,
        frame: IntArray,
    ): Boolean = frame.size > 1 && (request.size < 2 || frame[1] == (request[1].toInt() and 0xFF))

    /** Reads packet [code] from the watch; throws after [tries] unanswered or refused tries. */
    suspend fun request(
        code: Int,
        tries: Int = TRIES,
    ): IntArray {
        val request = requestFor(code)
        var last: Throwable = NoAnswer(code)
        for (attempt in 0 until tries) {
            try {
                return attempt(request, tryTimeoutMs) { true }
            } catch (e: Failure) {
                last = e
            } catch (e: NoAnswer) {
                last = e
            }
            Timber.w("0x%02X read try %d failed: %s".format(code, attempt + 1, last.message))
            if (attempt < tries - 1) delay(retryBaseDelayMs * (attempt + 1))
        }
        throw last
    }

    /**
     * One settings read: sends [request], waits up to [timeoutMs] (the watch answers within 10 s) and returns the first answer that
     * begins with the code and that [accept] takes. A refusal (`FF`) names its status; nothing is retried.
     */
    suspend fun read(
        request: ByteArray,
        timeoutMs: Long = READ_TIMEOUT_MS,
        accept: (ByteArray) -> Boolean = { true },
    ): Reply =
        try {
            val frame = attempt(request, timeoutMs) { it.size > 1 && accept(Utils.byteArrayOfIntArray(it)) }
            Reply(Utils.byteArrayOfIntArray(frame), null)
        } catch (e: Failure) {
            Reply(null, e.text)
        } catch (e: NoAnswer) {
            Reply(null, "The watch did not answer within ${timeoutMs / 1000} s.")
        }

    /** The answer to the default request of [code] as frame bytes (code first), or null; [read] says why. */
    suspend fun readFrame(code: Int): ByteArray? {
        val request = requestFor(code)
        return read(request, READ_TIMEOUT_MS) { answersRequest(request, it.map { b -> b.toInt() and 0xFF }.toIntArray()) }.frame
    }

    private suspend fun attempt(
        request: ByteArray,
        timeoutMs: Long,
        accept: (IntArray) -> Boolean,
    ): IntArray {
        val code = request[0].toInt() and 0xFF
        val key = request.joinToString("") { "%02X".format(it) }
        // A reader of the same request waits for the answer that is already asked for.
        synchronized(lock) { current?.takeIf { it.key == key && it.deferred.isActive } }?.let { return await(it, timeoutMs, code) }
        org.avmedia.gshockapi.casio.ConnectionSetup.awaitIdle()
        return sendLock.withLock {
            // A LifeLog transfer is running: it needs the link, so this read does not start.
            if (!WatchExchange.mutex.tryLock()) throw Failure("The watch is busy with a transfer.")
            val pending = Pending(key, code, accept)
            synchronized(lock) { current = pending }
            try {
                ConnectionKeepAlive.log("read tx " + request.toHex() + " (waits up to ${timeoutMs / 1000} s)")
                link.sendRequest(request)
                await(pending, timeoutMs, code)
            } finally {
                synchronized(lock) { if (current === pending) current = null }
                WatchExchange.mutex.unlock()
            }
        }
    }

    private suspend fun await(
        pending: Pending,
        timeoutMs: Long,
        code: Int,
    ): IntArray {
        val reply = withTimeoutOrNull(timeoutMs) { pending.deferred.await() }
        if (reply != null) return reply
        ConnectionKeepAlive.log("read 0x%02X: no answer within %d ms".format(code, timeoutMs))
        // readers that joined it retry on their own
        pending.deferred.completeExceptionally(NoAnswer(code))
        throw NoAnswer(code)
    }

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /**
     * Called by [org.avmedia.gshockapi.casio.MessageDispatcher] for every notification before anything else.
     * Takes the answer to the request that waits (first byte = its code), or an `FF` refusal while one waits;
     * returns false for everything else, which then goes to the library as usual.
     */
    fun offer(data: String): Boolean {
        val pending = synchronized(lock) { current } ?: return false
        val ints = runCatching { Utils.toIntArray(data).toIntArray() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return false
        if (ints[0] != pending.code && ints[0] != 0xFF) return false
        // Every answer that arrives while a read waits is logged (first bytes only), so a refused or lost answer can be seen.
        ConnectionKeepAlive.log("read 0x%02X rx %s".format(pending.code, ints.take(LOG_BYTES).toIntArray().toHex()))
        Timber.i("RawSettingIO received: ${ints.toHex()}")
        if (ints[0] == 0xFF) {
            val status = failureStatus(ints, pending.code)
            pending.deferred.completeExceptionally(
                Failure(if (status < 0) "watch refused: no status" else "watch refused: status %02X".format(status)),
            )
        } else if (pending.accept(ints)) {
            pending.deferred.complete(ints)
        }
        // The connection setup and the button info wait for these codes too: the frame goes on to them as well.
        return !(ints[0] != 0xFF && ints[0] in org.avmedia.gshockapi.casio.ConnectionSetup.WATCHED_CODES)
    }

    /** Handler registered in WatchProtocol.dataReceivedHandlers for the codes upstream reads; answers are taken by [offer]. */
    fun onReceived(data: String) {
        Timber.d("RawSettingIO: unsolicited answer ignored: $data")
    }

    /** The status of an `FF` answer: `FF status`, or `FF code status` (-1 when there is none). */
    fun failureStatus(
        ints: IntArray,
        code: Int,
    ): Int =
        when {
            ints.size >= 3 && ints[1] == code -> ints[2]
            ints.size >= 2 && ints[1] != code -> ints[1]
            else -> -1
        }

    private const val LOG_BYTES = 8

    /**
     * Read, change only [touched] bytes, write (the write result must be OK), read back and compare, serialized per code.
     * [change] gets a fresh copy of the frame and returns the frame to write, or null (refused) / an equal frame (no write).
     */
    suspend fun modifySafe(
        code: Int,
        touched: Set<Int>,
        change: (ByteArray) -> ByteArray?,
    ): SettingWriteResult = withLock(code) { safeWriter().update(code, touched) { change(it.copyOf()) } }

    /** The checked writer every setting write goes through: [read] defaults to [readFrame]. */
    fun safeWriter(read: suspend (Int) -> ByteArray? = ::readFrame): SafeSettingWrite =
        SafeSettingWrite(
            read = read,
            write = { frame ->
                Timber.i("RawSettingIO write: ${frame.toHex()}")
                if (!link.sendChecked(frame)) throw WriteNotConfirmed("no write result")
            },
            settle = { delay(settleMs) },
        )

    /** Runs [block] holding the per-code lock of [modifySafe] (for packets read through another IO). */
    suspend fun <T> withLock(
        code: Int,
        block: suspend () -> T,
    ): T = locks.getOrPut(code) { Mutex() }.withLock { block() }

    /** Writes a full packet (first byte = code) to the watch, not waited for. */
    fun write(packet: IntArray) {
        Timber.i("RawSettingIO write: ${packet.toHex()}")
        link.sendPacket(packet)
    }
}
