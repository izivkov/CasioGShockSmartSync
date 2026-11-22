package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** "42 05 3D …" */
internal fun IntArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/**
 * Generic read / write for settings packets whose first byte is the command code
 * (DW-H5600: 0x24, 0x39, 0x42, 0x45, 0x4F, 0x50, 0x51, and raw 0x13 restores).
 *
 * Read:  1-byte request [code] on the read-request characteristic (GetSetMode.GET, 26eb002c);
 *        the watch answers with a notification on 26eb002d, or `FF 81 <code>` when busy.
 * Write: the full packet on the all-features characteristic (GetSetMode.SET, 26eb002d).
 *
 * Reads are robust against what the watch actually does:
 *  - concurrent readers of the same code share one request (a second caller used to cancel the first);
 *  - a GET sent right after a SET is sometimes ignored, so each try is short and retried, and an
 *    unanswered request is discarded (later readers would otherwise join it forever);
 *  - a rejection fails the try at once instead of waiting for the timeout.
 * [modify] serializes read-modify-write per code, so two quick changes cannot undo each other.
 */
object RawSettingIO {
    private const val TRIES = 3

    // Seams for unit tests (a fake watch); production values below.
    internal var tryTimeoutMs = 3_000L
    internal var retryBaseDelayMs = 400L
    internal var sendRequest: (Int) -> Unit = { code -> IO.writeCmd(GetSetMode.GET, byteArrayOf(code.toByte())) }
    internal var sendPacket: (IntArray) -> Unit = { packet -> IO.writeCmd(GetSetMode.SET, Utils.byteArrayOfIntArray(packet)) }

    private val pending = mutableMapOf<Int, CompletableDeferred<IntArray>>()
    private val locks = ConcurrentHashMap<Int, Mutex>()

    private class Rejected(code: Int) : IllegalStateException("watch rejected 0x%02X (busy?)".format(code))

    /** Reads packet [code] from the watch; throws after [tries] unanswered or rejected tries. */
    suspend fun request(code: Int, tries: Int = TRIES): IntArray {
        var last: Throwable = IllegalStateException("no answer to 0x%02X".format(code))
        for (attempt in 0 until tries) {
            val (deferred, isNew) = synchronized(pending) {
                pending[code]?.takeIf { it.isActive }?.let { it to false }
                    ?: CompletableDeferred<IntArray>().also { pending[code] = it }.let { it to true }
            }
            if (isNew) sendRequest(code)
            try {
                val reply = withTimeoutOrNull(tryTimeoutMs) { deferred.await() }
                if (reply != null) return reply
                last = IllegalStateException("no answer to 0x%02X".format(code))
                discard(code, deferred, last)
            } catch (e: Rejected) {
                last = e
            } catch (e: NoAnswer) {
                last = e // another reader's try timed out on the shared request
            }
            Timber.w("0x%02X read try %d failed: %s".format(code, attempt + 1, last.message))
            if (attempt < tries - 1) delay(retryBaseDelayMs * (attempt + 1))
        }
        throw last
    }

    private class NoAnswer(code: Int) : IllegalStateException("no answer to 0x%02X".format(code))

    /**
     * Read-modify-write of packet [code], serialized per code. [transform] gets a fresh copy of the
     * packet and returns the packet to write, or null / an equal packet to skip the write.
     * Returns the packet as it was before the change.
     */
    suspend fun modify(code: Int, transform: (IntArray) -> IntArray?): IntArray =
        lock(code).withLock {
            val before = request(code)
            val after = transform(before.copyOf())
            if (after != null && !after.contentEquals(before)) write(after)
            before
        }

    /** Runs [block] holding the same per-code lock as [modify] (for packets read through another IO). */
    suspend fun <T> withLock(code: Int, block: suspend () -> T): T = lock(code).withLock { block() }

    private fun lock(code: Int) = locks.getOrPut(code) { Mutex() }

    /** Writes a full packet (first byte = code) to the watch. */
    fun write(packet: IntArray) {
        Timber.i("RawSettingIO write: ${packet.toHex()}")
        sendPacket(packet)
    }

    private fun discard(code: Int, deferred: CompletableDeferred<IntArray>, cause: Throwable) {
        synchronized(pending) { if (pending[code] === deferred) pending.remove(code) }
        deferred.completeExceptionally(NoAnswer(code)) // readers that joined it retry on their own
    }

    /** The watch answered `FF 81 <code>` (rejected): fail the pending read for that code now. */
    fun onRejected(code: Int) {
        synchronized(pending) { pending.remove(code) }?.completeExceptionally(Rejected(code))
    }

    /** Handler registered in WatchProtocol.dataReceivedHandlers for each supported code. */
    fun onReceived(data: String) {
        val ints = runCatching { Utils.toIntArray(data).toIntArray() }.getOrNull() ?: return
        if (ints.isEmpty()) return
        Timber.i("RawSettingIO received: ${ints.toHex()}")
        synchronized(pending) { pending.remove(ints[0]) }?.complete(ints)
    }
}
