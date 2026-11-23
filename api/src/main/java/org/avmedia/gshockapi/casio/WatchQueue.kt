package org.avmedia.gshockapi.casio

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.ble.GetSetMode
import timber.log.Timber

/** The GATT side of the queue: one write, done when Android reports it (with or without response), false when it failed. */
interface WatchTransport {
    suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean
}

/** How one request ended. [answer] is set for a read. */
class Outcome(val ok: Boolean, val reason: String? = null, val answer: ByteArray? = null) {
    companion object {
        fun failed(reason: String) = Outcome(false, reason)
    }
}

/**
 * One queue for every read request and every write to the watch, strictly one at a time and in the order asked (Casio's
 * request queue). A write is done when its write result came. A read is done when its answer came (first byte = the code, the
 * option bytes the table names equal), when an `FF` notification came (that read failed), or after [CommandTable.REQUEST_TIMEOUT_MS].
 * Nothing is retried. Notifications are never consumed here: the handlers of every code, unsolicited ones included, still get all
 * of them while a request waits.
 */
class WatchQueue(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    @Volatile var transport: WatchTransport,
    private val readTimeoutMs: Long = CommandTable.REQUEST_TIMEOUT_MS,
) {
    private class Item(val handle: GetSetMode, val data: ByteArray) {
        val done = CompletableDeferred<Outcome>()
    }

    private class Waiting(val frame: ByteArray) {
        val answer = CompletableDeferred<ByteArray>()
    }

    private val items = Channel<Item>(Channel.UNLIMITED)
    private val lock = Any()
    private var waiting: Waiting? = null

    init {
        scope.launch { for (item in items) launch { item.done.complete(runOne(item)) } }
    }

    /** Puts the frame at the end of the queue. The result completes when the request is done. */
    fun enqueue(handle: GetSetMode, data: ByteArray): Deferred<Outcome> {
        val item = Item(handle, data.copyOf())
        if (items.trySend(item).isFailure) item.done.complete(Outcome.failed("queue closed"))
        return item.done
    }

    /** Fails everything that waits (a link that is gone). */
    fun clear(reason: String) {
        synchronized(lock) { waiting?.answer?.complete(byteArrayOf(0xFF.toByte())) }
        while (true) {
            val item = items.tryReceive().getOrNull() ?: break
            item.done.complete(Outcome.failed(reason))
        }
    }

    /** A notification came in on [characteristic]. Only the all-features characteristic can answer a read. */
    fun onNotification(characteristic: UUID, bytes: ByteArray) {
        if (characteristic != CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID || bytes.isEmpty()) return
        val w = synchronized(lock) { waiting } ?: return
        // Every notification that arrives while a read waits is kept in connection.log (first bytes), FF answers included.
        ConnectionKeepAlive.log("read 0x%02X waits, rx %s".format(w.frame[0].toInt() and 0xFF, bytes.take(10).joinToString(" ") { "%02X".format(it) }))
        if (CommandTable.matches(w.frame, bytes)) w.answer.complete(bytes)
    }

    private suspend fun runOne(item: Item): Outcome =
        try {
            when (val verdict = CommandTable.check(item.handle, item.data)) {
                is Verdict.Reject -> {
                    Timber.w("Watch request refused: ${verdict.reason}")
                    Outcome.failed(verdict.reason)
                }
                is Verdict.Send -> send(WatchChannel.of(item.handle), verdict).also {
                    if (!it.ok) ConnectionKeepAlive.log("request %02X failed: %s".format(item.data[0].toInt() and 0xFF, it.reason))
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.e(t, "Watch request failed")
            Outcome.failed(t.message ?: "error")
        }

    private suspend fun send(channel: WatchChannel, verdict: Verdict.Send): Outcome {
        if (channel != WatchChannel.READ_REQUEST) {
            val ok = transport.write(channel, verdict.frame)
            return if (ok) Outcome(true) else Outcome.failed("The write to the watch failed.")
        }
        val w = Waiting(verdict.frame)
        synchronized(lock) { waiting = w }
        try {
            if (!transport.write(channel, verdict.frame)) return Outcome.failed("The read request could not be sent.")
            val limit = verdict.spec?.timeoutMs ?: readTimeoutMs
            val answer = withTimeoutOrNull(minOf(limit, readTimeoutMs)) { w.answer.await() }
                ?: return Outcome.failed("The watch did not answer within ${readTimeoutMs / 1000} s.")
            if (answer[0] == 0xFF.toByte()) {
                val status = answer.getOrNull(1)?.let { "%02X".format(it) } ?: "none"
                return Outcome.failed("The watch refused the read (status $status).")
            }
            if (!CommandTable.longEnough(verdict.frame, answer)) return Outcome(false, "The answer is shorter than the layout needs.", answer)
            return Outcome(true, null, answer)
        } finally {
            synchronized(lock) { waiting = null }
        }
    }
}

/** The queue of the real link. The transport is set by the connection code; a fake in the unit tests. */
object WatchLink {
    private object NoLink : WatchTransport {
        override suspend fun write(channel: WatchChannel, frame: ByteArray) = false
    }

    @Volatile var queue: WatchQueue = WatchQueue(transport = NoLink)

    fun setTransport(transport: WatchTransport) {
        queue.transport = transport
    }

    /** Puts a request or write in the queue (fire and forget). */
    fun write(handle: GetSetMode, data: ByteArray) {
        queue.enqueue(handle, data)
    }

    /** Like [write], and waits for the result: true when the write result came (or, for a read, the answer). */
    suspend fun writeChecked(handle: GetSetMode, data: ByteArray): Boolean = queue.enqueue(handle, data).await().ok

    private val source = ThreadLocal<UUID?>()

    /** Runs [block] (the delivery of one notification) and says on which characteristic it came, for code that routes by it. */
    fun <T> withSource(characteristic: UUID, block: () -> T): T {
        val before = source.get()
        source.set(characteristic)
        try {
            return block()
        } finally {
            source.set(before)
        }
    }

    /** The characteristic of the notification being delivered on this thread, null outside a delivery. */
    fun currentSource(): UUID? = source.get()

    /** The notification hook of the connection: [hex] as the manager logs it ("0x28 06 1D"). */
    fun onNotification(characteristic: UUID, hex: String?) {
        queue.onNotification(characteristic, parseHex(hex))
    }

    fun parseHex(hex: String?): ByteArray {
        if (hex == null) return ByteArray(0)
        return hex.trim().split(' ').filter { it.isNotBlank() }
            .mapNotNull { it.removePrefix("0x").toIntOrNull(16)?.toByte() }
            .toByteArray()
    }
}
