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
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.WatchLink
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

    /**
     * Which characteristic a message belongs to while a workout or tide transfer runs. The characteristic it came on decides;
     * the guess from the bytes is only the fallback when the connection code did not say. Owner log 2026-11-23_2309: the
     * watch's `09 1D` (all sent) is longer than 2 bytes, the guess called it a status frame and the read never answered it.
     */
    fun channelOf(hex: String): H5600Channel =
        when (WatchLink.currentSource()) {
            CasioConstants.CASIO_DATA_REQUEST_CHARACTERISTIC_UUID -> H5600Channel.DRSP
            CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID -> H5600Channel.CONVOY
            null -> channelOf(H5600HealthIO.bytesOf(hex))
            else -> H5600Channel.STATUS
        }

    /** The guess from the bytes alone (no characteristic known). Single bytes are never bulk. */
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
        val box = inbox ?: return
        val data = H5600HealthIO.bytesOf(hex)
        // Every frame whole: the raw rx line shows only two bytes, too few to see a chunk or an end message.
        log("workout rx " + (if (channel == H5600Channel.CONVOY) "bulk " else "drsp ") + data.joinToString(" ") { "%02X".format(it) })
        box.trySend(WorkoutMsg(channel, data))
    }

    /** After the connection setup: `0x28` index 6 (one byte after the code, so list position 7) is 1 or 2 when the watch holds workouts. */
    fun onSetupDone(condition: List<Int>) {
        val flag = condition.getOrNull(WorkoutReadTrigger.FLAG_POSITION) ?: return
        if (!WorkoutReadTrigger.waiting(flag)) return
        log("Workout read: 0x28 index 6 is $flag after the setup, workouts are waiting")
        scope.launch { download() }
    }

    /** End of the last download that finished (with or without workouts); null until one did. */
    @Volatile
    var lastCheckMs: Long? = null
        internal set

    /** After a LifeLog read ([condition] = its raw `0x28` answer, code byte first): download when [WorkoutReadTrigger] says so. */
    fun afterRead(condition: ByteArray?, nowMs: Long = System.currentTimeMillis()) {
        if (sink == null) return
        val flag = WorkoutReadTrigger.flagOf(condition)
        if (!WorkoutReadTrigger.shouldDownload(flag, lastCheckMs, nowMs)) return
        log("Workout read after the LifeLog read: " + if (WorkoutReadTrigger.waiting(flag)) "0x28 index 6 is $flag" else "hourly check (index 6 is $flag)")
        scope.launch { download() }
    }

    /** After a failed read an automatic one waits this long; the button always reads (owner log 2026-11-23j: six tries in 14 min). */
    const val RETRY_AFTER_FAIL_MS = 10 * 60_000L

    /** A test seam for the back-off. */
    @Volatile internal var clock: () -> Long = System::currentTimeMillis

    /** How long a read waits for `47 01` (the watch's own setup) before it goes on anyway. */
    @Volatile internal var setupWaitMs: Long = org.avmedia.gshockapi.casio.ConnectionSetup.SETUP_WAIT_MS

    /** When the last read failed; null after a read that finished. */
    @Volatile private var lastFailMs: Long? = null

    /** An automatic read runs or waits for the link: a second automatic one is not queued behind it. */
    private val automaticPending = java.util.concurrent.atomic.AtomicBoolean(false)

    internal fun resetBackoff() {
        lastFailMs = null
    }

    /**
     * One download run; waits for the exchange lock (and says so). Returns the number of workouts stored, or null when it did not run.
     * [userAsked] = the button: it always runs; an automatic read is skipped while another one runs or waits, and for
     * [RETRY_AFTER_FAIL_MS] after a failed one.
     */
    suspend fun download(userAsked: Boolean = false): WorkoutRunResult? {
        log("Workout read asked" + if (userAsked) " (button)" else "")
        if (sink == null) return null.also { log("Workout read not started: the app keeps no workouts") }
        if (!userAsked && !automaticPending.compareAndSet(false, true)) return null.also { log("Workout read not started: one is already running or waiting") }
        try {
            if (!userAsked) backedOff()?.let { log(it); return null }
            if (!WatchExchange.mutex.tryLock()) {
                log("Workout read waits: another exchange (a LifeLog or settings read) holds the link")
                WatchExchange.mutex.lock()
                log("Workout read: the link is free")
            }
            try {
                return downloadHeld(userAsked)
            } finally {
                WatchExchange.mutex.unlock()
            }
        } finally {
            if (!userAsked) automaticPending.set(false)
        }
    }

    /** The log line when an automatic read must wait after a failure, else null. */
    private fun backedOff(): String? {
        val failed = lastFailMs ?: return null
        val left = failed + RETRY_AFTER_FAIL_MS - clock()
        if (left <= 0) return null
        return "Workout read not started: the last one failed, next automatic try in ${(left + 59_999) / 60_000} min (the button reads now)"
    }

    /** Like the LifeLog: no transfer before the watch's setup is done (`47 01`) and our own setup is idle. Owner log 2026-11-23j 22:27:39. */
    private suspend fun awaitWatchSetup() {
        org.avmedia.gshockapi.casio.ConnectionSetup.awaitIdle()
        if (org.avmedia.gshockapi.casio.ConnectionSetup.watchSetupDone) return
        log("Workout read waits for 47 01 (the watch's setup)")
        val came = kotlinx.coroutines.withTimeoutOrNull(setupWaitMs) {
            while (!org.avmedia.gshockapi.casio.ConnectionSetup.watchSetupDone) kotlinx.coroutines.delay(100)
            true
        }
        log(if (came == true) "Workout read: 47 01 came" else "Workout read: no 47 01 in ${setupWaitMs / 1000} s, going on")
    }

    /**
     * Inside a LifeLog read (exchange lock held), before its START: when its `0x28` answer flags workouts (index 6 is 1 or 2)
     * they are read first ([run], normally [downloadHeld]), as Casio's app does (notes T4: workouts, then the LifeLog). Owner
     * log 2026-11-23h 21:31:39: the first `0x28` ever with index 6 = 1, and a START the watch did not answer for 60 s.
     */
    suspend fun beforeLifeLog(condition: ByteArray?, run: suspend () -> Unit) {
        val flag = WorkoutReadTrigger.flagOf(condition)
        if (!WorkoutReadTrigger.waiting(flag)) return
        log("LifeLog: 0x28 index 6 is $flag, the waiting workouts are read first")
        try {
            run()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            log("Workout read before the LifeLog failed: ${t.javaClass.simpleName} ${t.message.orEmpty()}")
        }
        log("LifeLog read goes on after the workouts")
    }

    /**
     * One download run for a caller that holds [WatchExchange.mutex]: the LifeLog read runs it before its START when `0x28`
     * flags workouts, as Casio's app does (notes T4: workouts first, then the LifeLog).
     */
    suspend fun downloadHeld(userAsked: Boolean = false): WorkoutRunResult? {
        val store = sink ?: return null.also { log("Workout read not started: the app keeps no workouts") }
        if (!userAsked) backedOff()?.let { log(it); return null }
        if (!running.compareAndSet(false, true)) return null.also { log("Workout read not started: one is already running") }
        try {
            awaitWatchSetup()
        } catch (t: Throwable) {
            running.set(false)
            throw t
        }
        log("Workout read starts")
        val channel = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val frames = mutableListOf<ByteArray>()
        val stream = java.io.ByteArrayOutputStream()
        try {
            _status.value = "Downloading workouts"
            lastError = null
            inbox = channel
            val transfer = WorkoutTransfer(
                link = link,
                inbox = channel,
                sink = { s -> store(s).also { ok -> if (ok) _workouts.emit(s) } },
                blockCache = blockCache,
                capture = { m, bytes ->
                    m?.let { frames += it.data }
                    bytes?.let { stream.write(it) }
                },
                log = ::log,
            )
            val result = transfer.run()
            lastError = result.error
            lastFailMs = if (result.error == null) null else clock()
            if (result.error == null) lastCheckMs = System.currentTimeMillis()
            log(
                if (result.error == null) "Workout read done: ${result.downloaded} new stored"
                else "Workout read stopped: ${result.error}" + if (result.downloaded > 0) " (${result.downloaded} stored before)" else "",
            )
            keepDump(frames, stream.toByteArray(), result.error)
            return result
        } catch (t: Throwable) {
            lastFailMs = clock()
            log("Workout read stopped: ${t.javaClass.simpleName} ${t.message.orEmpty()}")
            throw t
        } finally {
            inbox = null
            org.avmedia.gshockapi.casio.ConnectionSetup.onTransferEnded()
            _status.value = null
            running.set(false)
        }
    }

    private fun log(text: String) {
        Timber.i("H5600 workouts: $text")
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log(text)
    }

    /** The two characteristics; a test seam. */
    @Volatile
    internal var link: WorkoutLink = object : WorkoutLink {
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

    private val DRSP_FIRST = setOf(0x00, 0x03, 0x04, 0x06, 0x07, 0x08, 0x09)
    private const val BULK_LAST = 0x07
}
