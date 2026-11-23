package org.avmedia.gshockapi.casio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockapi.ble.GetSetMode
import timber.log.Timber

/**
 * What the watch said about notification texts: the last command of the notification manager (`0x3D`, from the watch) and the
 * MTU it reports in the watch condition (`0x28`, value index 4 and 5, low byte first).
 */
object NotificationGate {
    /** Commands that stop the app from sending: pause (data transfer, setting operation, activity) and stop (switched off). */
    private val STOPPED = setOf(0x10, 0x11, 0x12, 0x20)

    /** Commands that start it: by the user, woke up, receiving again. */
    private val STARTED = setOf(0x00, 0x01, 0x02)

    @Volatile private var command = -1

    @Volatile var mtu = 0
        private set

    fun reset() {
        command = -1
        mtu = 0
    }

    /** An unasked `3D command ...` from the watch. The value of our own read (`3D 30 ...`) is not a command and is ignored. */
    fun onManager(data: String) {
        val b = WatchLink.parseHex(data)
        val value = (b.getOrNull(1)?.toInt() ?: return) and 0xFF
        if (value in STARTED || value in STOPPED) command = value
    }

    /** The `0x28` answer: the MTU is the 16 bit value at value index 4 and 5. */
    fun onCondition(data: String) {
        val b = WatchLink.parseHex(data)
        if (b.size < 7 || b[0] != 0x28.toByte()) return
        val value = (b[5].toInt() and 0xFF) or ((b[6].toInt() and 0xFF) shl 8)
        if (value in 23..517) mtu = value
    }

    /** False after a pause or stop command; before the first command the watch has said nothing and sending is allowed. */
    fun allowed(): Boolean = command !in STOPPED

    /** The bytes per chunk: the MTU minus 4, and 20 until the watch condition was read. */
    fun chunkSize(): Int = if (mtu > 0) mtu - 4 else 20
}

/**
 * Sends a notification text the way Casio's app does (notes T4): on the notification characteristic, without response, in chunks of
 * the MTU minus 4 bytes, the next chunk [spacingMs] after the write result of the one before, a failed write drops the rest, and
 * only while the watch allows it and no LifeLog or workout transfer runs (then the text is held back and sent after it). One text
 * at a time.
 */
object NotificationSender {
    /** 2000 ms for the DW-H5600 family, 500 ms for other models. */
    @Volatile var spacingMs = LongProcess.NOTIFICATION_TEXTS.chunkGapMs ?: 2_000L

    /** True while a transfer holds the link; the default asks the transfers. */
    @Volatile var busy: () -> Boolean = {
        org.avmedia.gshockapi.io.H5600HealthIO.isActive || org.avmedia.gshockapi.io.H5600WorkoutIO.isActive
    }

    /** One write on the notification characteristic; true when its write result came. */
    @Volatile var writer: suspend (ByteArray) -> Boolean = { WatchLink.writeChecked(GetSetMode.NOTIFY, it) }

    @Volatile var holdLimitMs = 10 * 60_000L

    @Volatile var holdPollMs = 500L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun chunks(buffer: ByteArray, size: Int): List<ByteArray> =
        if (size <= 0) emptyList() else buffer.toList().chunked(size).map { it.toByteArray() }

    /** Queues [buffer] (already inverted). The job ends when the text is sent, dropped or held back too long. */
    fun send(buffer: ByteArray): Job =
        scope.launch {
            mutex.withLock {
                var held = 0L
                while (!NotificationGate.allowed() || busy()) {
                    if (held >= holdLimitMs) {
                        Timber.w("Notification text dropped: the watch did not allow it for ${holdLimitMs / 1000} s")
                        return@withLock
                    }
                    delay(holdPollMs)
                    held += holdPollMs
                }
                val pieces = chunks(buffer, NotificationGate.chunkSize())
                for ((i, piece) in pieces.withIndex()) {
                    if (!writer(piece)) {
                        Timber.w("Notification text: chunk ${i + 1} of ${pieces.size} failed, the rest is dropped")
                        return@withLock
                    }
                    if (i < pieces.size - 1) delay(spacingMs)
                }
            }
        }
}
