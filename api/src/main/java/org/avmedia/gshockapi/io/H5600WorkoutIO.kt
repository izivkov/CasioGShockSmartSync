package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.model.WorkoutSession
import timber.log.Timber

/**
 * Downloads the workouts of a DW-H5600 (notes H) through the shared exchange lock.
 *
 * The app sets [sink] (store the workout; true when stored) and may collect [workouts] to be told about each new one.
 * Without a sink nothing is downloaded, so nothing is marked as fetched that nobody keeps. A workout is delivered
 * before it is marked, so a crash in between delivers it again: the app stores by start time and sport (upsert).
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600WorkoutIO {
    /** Called for every downloaded workout, before the fetched mark; return true only when it is stored. */
    @Volatile
    var sink: (suspend (WorkoutSession) -> Boolean)? = null

    /** Raw bytes of every workout transfer, failed or not (folder prefix "workout-"); null keeps nothing. */
    @Volatile
    var rawDump: H5600RawDump? = null

    private val _workouts = MutableSharedFlow<WorkoutSession>(extraBufferCapacity = 64)

    /** Each workout after the sink took it. A notice only; storing is the sink's job. */
    val workouts: SharedFlow<WorkoutSession> = _workouts

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** Why the last run stopped early; null when it finished. */
    @Volatile
    var lastError: String? = null
        private set

    @Volatile private var inbox: Channel<WorkoutMsg>? = null
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private val blockCache = HashMap<String, ByteArray>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isActive: Boolean get() = inbox != null

    /** Which characteristic a message belongs to while a workout transfer runs. Single bytes are never bulk. */
    fun channelOf(hex: String): H5600Channel = channelOf(H5600HealthIO.bytesOf(hex))

    fun channelOf(data: ByteArray): H5600Channel {
        val first = data.firstOrNull()?.toInt()?.and(0xFF) ?: return H5600Channel.STATUS
        return when {
            data.size <= 2 && first in DRSP_FIRST -> H5600Channel.DRSP
            data.size > 2 && first <= BULK_LAST -> H5600Channel.CONVOY
            else -> H5600Channel.STATUS
        }
    }

    fun onDrsp(hex: String) = push(H5600Channel.DRSP, hex)

    fun onConvoy(hex: String) = push(H5600Channel.CONVOY, hex)

    private fun push(channel: H5600Channel, hex: String) {
        inbox?.trySend(WorkoutMsg(channel, H5600HealthIO.bytesOf(hex)))
    }

    /** After the connection setup: `0x28` index 6 (one byte after the code, so list position 7) is 1 or 2 when the watch holds workouts. */
    fun onSetupDone(condition: List<Int>) {
        val flag = condition.getOrNull(CONDITION_POSITION) ?: return
        if (flag != 1 && flag != 2) return
        scope.launch { download() }
    }

    /** One download run; waits for the exchange lock. Returns the number of workouts stored, or null when it did not run. */
    suspend fun download(): WorkoutRunResult? {
        val store = sink ?: return null.also { Timber.w("H5600 workouts: no sink set, nothing downloaded") }
        if (!running.compareAndSet(false, true)) return null
        WatchExchange.mutex.lock()
        val channel = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val log = mutableListOf<ByteArray>()
        val stream = java.io.ByteArrayOutputStream()
        try {
            _status.value = "Downloading workouts"
            lastError = null
            inbox = channel
            val transfer = WorkoutTransfer(
                link = ioLink,
                inbox = channel,
                sink = { s -> store(s).also { ok -> if (ok) _workouts.emit(s) } },
                blockCache = blockCache,
                capture = { m, bytes ->
                    m?.let { log += it.data }
                    bytes?.let { stream.write(it) }
                },
            )
            val result = transfer.run()
            lastError = result.error
            if (result.error != null) Timber.w("H5600 workouts: ${result.error}")
            keepDump(log, stream.toByteArray(), result.error)
            return result
        } finally {
            inbox = null
            _status.value = null
            running.set(false)
            WatchExchange.mutex.unlock()
        }
    }

    private val ioLink = object : WorkoutLink {
        override fun drsp(data: ByteArray) = IO.writeCmd(GetSetMode.DATA_REQUEST, data)

        override fun bulk(data: ByteArray) = IO.writeCmd(GetSetMode.CONVOY, data)
    }

    private fun keepDump(log: List<ByteArray>, stream: ByteArray, failure: String?) {
        val dump = rawDump ?: return
        try {
            dump.save(H5600Capture(log, emptyMap(), stream, failure), System.currentTimeMillis())
        } catch (t: Throwable) {
            Timber.w(t, "H5600 workouts: could not keep the raw transfer")
        }
    }

    private const val CONDITION_POSITION = 7
    private val DRSP_FIRST = setOf(0x00, 0x03, 0x04, 0x06, 0x07, 0x08, 0x09)
    private const val BULK_LAST = 0x07
}
