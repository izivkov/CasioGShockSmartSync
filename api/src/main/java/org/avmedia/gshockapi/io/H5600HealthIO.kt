package org.avmedia.gshockapi.io

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

/**
 * Imperative shell for the DW-H5600 LifeLog transfer.
 *
 * Runs one transfer at a time. Notifications arrive through [onDrsp], [onConvoy] and [onStatus],
 * which [org.avmedia.gshockapi.protocols.H5600Protocol] registers. The sequencing lives in
 * [H5600LifeLogSync]; this object performs its writes and collects the result for
 * [H5600HealthDecoder].
 *
 * Every log line starts with "H5600:" so the transfer can be followed with
 * `adb logcat | grep H5600`.
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600HealthIO {
    private val SETUP_REPLY_TIMEOUT_MS = org.avmedia.gshockapi.casio.LongProcess.LIFELOG.answerMs!!
    private const val OVERALL_TIMEOUT_MS = 180_000L
    private const val STEP_GOAL_COMMAND = 0x43
    private const val WATCH_CONDITION_COMMAND = 0x28
    private const val ANNOUNCEMENT_COMMAND = 0x00
    private const val ANNOUNCEMENT_BYTES = 6
    private const val BULK_LOG_EVERY = 50
    private const val HEX_PREVIEW_BYTES = 16
    private const val POLL_REPLY_TIMEOUT_MS = 1_000L
    /** How long an attempt waits for the watch to report ready after START (observed: 6 to 50 s). */
    @Volatile internal var readyWaitMs = 60_000L

    /** What a read on a set-up link asks before START: the step goal and the watch condition, both used by the decoder. */
    internal val QUICK_SETUP_COMMANDS: List<Int> = listOf(STEP_GOAL_COMMAND, WATCH_CONDITION_COMMAND)
    private const val MAX_TRANSFER_ATTEMPTS = 3

    private class Transfer(
        val sync: H5600LifeLogSync,
        val zone: ZoneId,
    ) {
        val notifications = mutableListOf<ByteArray>()
        val announcements = mutableMapOf<Int, Int>()
        val setupReplies = Channel<ByteArray>(Channel.UNLIMITED)
        val convoyReplies = Channel<Unit>(Channel.UNLIMITED)
        val finished = CompletableDeferred<Unit>()
        var watchCondition: ByteArray? = null
        var bulkCount = 0
        var silent = false

        /** The link dropped during this attempt. */
        @Volatile var lost = false
    }

    /** Runs after every decoded read with its `0x28` answer: starts the workout download (it waits for the exchange lock). */
    @Volatile
    var afterRead: (ByteArray?) -> Unit = { H5600WorkoutIO.afterRead(it) }

    /**
     * The workout read [H5600WorkoutIO.beforeLifeLog] runs before START when `0x28` flags workouts; a test seam. Always read,
     * like a button press, without the back-off: a Health read is how workouts are read, and with one waiting the watch does
     * not answer the LifeLog START.
     */
    @Volatile
    var workoutsFirst: suspend () -> Unit = { H5600WorkoutIO.downloadHeld(userAsked = true) }

    private val lock = Any()
    private var active: Transfer? = null

    /** The workouts were read (or tried) before START in this Health read. */
    @Volatile private var workoutsTried = false

    private val _status = MutableStateFlow<String?>(null)

    /** What the transfer is doing when it is not simply running, for example a restart. */
    val status: StateFlow<String?> = _status

    /** Why the last transfer produced no data, for the UI. Null when the last transfer worked. */
    @Volatile
    var lastError: String? = null
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isActive: Boolean
        get() = synchronized(lock) { active != null }

    fun channelOf(hex: String): H5600Channel = H5600LifeLogSync.channelOf(bytesOf(hex))

    fun onDrsp(hex: String) = onNotification(H5600Channel.DRSP, hex)

    fun onConvoy(hex: String) = onNotification(H5600Channel.CONVOY, hex)

    fun onStatus(hex: String) = onNotification(H5600Channel.STATUS, hex)

    private var requestRunning = false

    /** When the running request started, for the read time in the app log. */
    @Volatile private var requestStartedMs = 0L

    /** Whether the connected watch has the LifeLog; a test seam. */
    @Volatile
    internal var stepCounterSupported: () -> Boolean = { WatchInfo.hasStepCounter }

    /**
     * The link dropped: a running read ends now. Before, it ran on into the next link, sent its setup there and took the
     * init's 0x10 answer.
     */
    fun onLinkLost() {
        val transfer =
            synchronized(lock) {
                if (requestRunning) linkLostDuringRequest = true
                active
            } ?: return
        transfer.lost = true
        transfer.setupReplies.close()
        transfer.finished.complete(Unit)
    }

    /** The protocol driver of one attempt. A peek read echoes END too: the watch drops the link without it. */
    @Suppress("UNUSED_PARAMETER")
    internal fun syncFor(peek: Boolean): H5600LifeLogSync = H5600LifeLogSync()

    /** True while a setup request is waiting for its reply. Only then does the transfer take status notifications. */
    @Volatile
    private var setupWaiting = false

    fun isAwaitingSetupReply(): Boolean = setupWaiting

    private class AttemptOutcome(val data: StepCounterData, val restart: Boolean, val lost: Boolean = false)

    /** Set when the link drops while a request runs: no further attempt starts on the next link. */
    @Volatile
    private var linkLostDuringRequest = false

    /**
     * Runs the LifeLog transfer. If the watch stops answering an attempt, that attempt is restarted from
     * the setup, up to [MAX_TRANSFER_ATTEMPTS] times, and [status] says so.
     */
    suspend fun request(peek: Boolean = true): StepCounterData {
        if (!stepCounterSupported()) {
            Timber.i("H5600: step counter not supported on model ${WatchInfo.model}")
            return StepCounterData.unavailable()
        }
        val started =
            synchronized(lock) {
                if (requestRunning) {
                    false
                } else {
                    requestRunning = true
                    linkLostDuringRequest = false
                    true
                }
            }
        if (!started) {
            Timber.w("H5600: request ignored, a transfer is already running")
            return StepCounterData.unavailable()
        }
        lastError = null
        workoutsTried = false
        requestStartedMs = System.currentTimeMillis()
        var locked = false
        try {
            // The connection setup runs first and the watch's own setup ends with `47 01`: before that no transfer and no setup
            // reads of ours (they took the setup's answers). Without `47 01` the read is left out on this link.
            if (!org.avmedia.gshockapi.casio.ConnectionSetup.watchSetupDone) {
                org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog read waits for 47 01 (the watch's setup)")
            }
            if (!org.avmedia.gshockapi.casio.ConnectionSetup.awaitWatchSetup()) {
                org.avmedia.gshockapi.casio.ConnectionKeepAlive.log(
                    "LifeLog read left out: no 47 01 in ${org.avmedia.gshockapi.casio.ConnectionSetup.watchSetupWaitMs / 1000} s on this link",
                )
                lastError = "The watch has not finished its setup on this connection. The LifeLog is read at the next connection."
                return StepCounterData.unavailable()
            }
            // No START in the middle of a time or settings exchange (the reads of the time exchange are counted one by one).
            WatchExchange.awaitNoSettingsExchange()
            // One exchange on the link at a time: a raw or settings read in progress finishes first.
            WatchExchange.mutex.lock()
            locked = true
            for (attempt in 1..MAX_TRANSFER_ATTEMPTS) {
                _status.value =
                    if (attempt == 1) {
                        null
                    } else {
                        "LifeLog data failed, trying again (attempt $attempt of $MAX_TRANSFER_ATTEMPTS)"
                    }
                val outcome =
                    if (linkLostDuringRequest) AttemptOutcome(StepCounterData.unavailable(), restart = false, lost = true)
                    else runAttempt(ZoneId.systemDefault(), peek)
                if (outcome.lost || linkLostDuringRequest) {
                    lastError = "The watch disconnected during the LifeLog read. It is read again at the next connection."
                    org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog read stopped: the link dropped (attempt $attempt)")
                    return StepCounterData.unavailable()
                }
                if (!outcome.restart) return outcome.data
                Timber.w("H5600: the watch did not answer; attempt $attempt of $MAX_TRANSFER_ATTEMPTS failed")
                org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog attempt $attempt of $MAX_TRANSFER_ATTEMPTS failed: the watch did not answer")
            }
            lastError =
                "LifeLog data could not be read after $MAX_TRANSFER_ATTEMPTS tries. Open the Health screen to try again."
            Timber.e("H5600: giving up after $MAX_TRANSFER_ATTEMPTS attempts")
            org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog read failed after $MAX_TRANSFER_ATTEMPTS tries")
            return StepCounterData.unavailable()
        } finally {
            _status.value = null
            synchronized(lock) { requestRunning = false }
            if (locked) WatchExchange.mutex.unlock()
        }
    }

    /** One attempt: setup, START, polls, then the data. restart = true when the watch stopped answering. */
    private suspend fun runAttempt(zone: ZoneId, peek: Boolean): AttemptOutcome {
        val transfer = Transfer(syncFor(peek), zone)
        synchronized(lock) { active = transfer }
        try {
            Timber.i("H5600: LifeLog attempt start (peek=$peek, zone=$zone)")
            org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog read starts (peek=$peek)")
            val stepGoal = runSetup(transfer)
            if (transfer.lost) return AttemptOutcome(StepCounterData.unavailable(), restart = false, lost = true)
            Timber.i("H5600: setup finished, step goal=$stepGoal")
            // One workout read per Health read: a restarted LifeLog attempt does not start it again.
            val workoutsRead = if (workoutsTried) {
                org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog: the workouts were tried in this Health read, not again")
                true
            } else {
                H5600WorkoutIO.beforeLifeLog(transfer.watchCondition, workoutsFirst).also { workoutsTried = it }
            }
            if (transfer.lost) return AttemptOutcome(StepCounterData.unavailable(), restart = false, lost = true)

            // The official app and the Python script write START before the first poll. The watch
            // acknowledges it on DRSP once it is ready. Without this write the first poll gets no reply.
            send(H5600Write(H5600Channel.DRSP, H5600LifeLogSync.START))

            for (write in synchronized(lock) { transfer.sync.start() }) {
                send(write)
            }

            // Re-polls while the watch is silent, and gives up on this attempt after a few silent polls.
            val watchdog = scope.launch { keepPolling(transfer) }
            val completed =
                try {
                    withTimeoutOrNull(OVERALL_TIMEOUT_MS.milliseconds) { transfer.finished.await() } != null
                } finally {
                    watchdog.cancel()
                }
            if (transfer.lost) return AttemptOutcome(StepCounterData.unavailable(), restart = false, lost = true)
            if (transfer.silent) {
                return AttemptOutcome(StepCounterData.unavailable(), restart = true)
            }
            if (!completed) {
                lastError = "The sync did not finish. Open the Health screen to try again."
            }

            val snapshot = synchronized(lock) { transfer.notifications.toList() to transfer.announcements.toMap() }
            Timber.i(
                "H5600: transfer ${if (completed) "finished" else "TIMED OUT"}: " +
                        "${snapshot.first.size} CONVOY notifications, announcements=${snapshot.second}",
            )

            val decoded =
                H5600HealthDecoder.decode(
                    notifications = snapshot.first,
                    announcedLengths = snapshot.second,
                    zone = transfer.zone,
                    stepGoal = stepGoal,
                    watchCondition = transfer.watchCondition,
                )
            if (decoded == null) {
                val stream = H5600HealthDecoder.lifeLogStream(snapshot.first, snapshot.second)
                Timber.w("H5600: no LifeLog header in ${stream.size} stream bytes; first bytes ${hex(stream, 20)}")
                org.avmedia.gshockapi.log.AppLog.log(org.avmedia.gshockapi.log.LogSource.Health, "LifeLog read: no LifeLog header in ${stream.size} stream bytes; first bytes ${hex(stream, 20)}")
                return AttemptOutcome(StepCounterData.unavailable(), restart = false)
            }
            Timber.i(
                "H5600: decoded steps=${decoded.currentDaySteps} goal=$stepGoal " +
                        "hours=${decoded.hourlyByHour.withIndex().filter { (it.value ?: 0) > 0 }.map { it.index }} " +
                        "warnings=${decoded.warnings.size}",
            )
            org.avmedia.gshockapi.casio.ConnectionKeepAlive.log(
                "LifeLog read done: steps ${decoded.currentDaySteps} in %.1f s".format((System.currentTimeMillis() - requestStartedMs) / 1000.0),
            )
            // Workouts read before START are not read a second time after the LifeLog.
            if (!workoutsRead) runCatching { afterRead(transfer.watchCondition) }.onFailure { Timber.w(it, "H5600: the step after the read failed") }
            return AttemptOutcome(decoded, restart = false)
        } finally {
            setupWaiting = false
            synchronized(lock) { active = null }
            org.avmedia.gshockapi.casio.ConnectionSetup.onTransferEnded()
            WatchExchange.onTransferEnded()
            transfer.setupReplies.close()
            Timber.i("H5600: attempt end")
        }
    }

    /**
     * Polls the watch until it reports ready. A busy reply (`00 01`) and a poll without a reply both mean "still preparing": the
     * watch answers a few polls busy, then goes silent while it prepares and answers the next poll ready (about
     * 6.6 s after START). Before, five such polls ended the attempt 0.2 s before the watch was ready
     * and the second attempt replayed the whole setup (20 s lost on every read). Now the attempt ends only when the watch has not
     * been ready for [readyWaitMs].
     */
    private suspend fun keepPolling(transfer: Transfer) {
        val started = System.currentTimeMillis()
        var notReady = 0
        while (!transfer.finished.isCompleted) {
            val reply = withTimeoutOrNull(POLL_REPLY_TIMEOUT_MS.milliseconds) { transfer.convoyReplies.receive() }
            if (!synchronized(lock) { transfer.sync.isPolling }) break
            notReady++
            if (System.currentTimeMillis() - started > readyWaitMs) {
                Timber.w("H5600: watch not ready after ${readyWaitMs / 1000} s ($notReady polls); restarting the attempt")
                transfer.silent = true
                transfer.finished.complete(Unit)
                break
            }
            if (reply == null) {
                Timber.w("H5600: no reply to poll within $POLL_REPLY_TIMEOUT_MS ms, retry #$notReady")
            } else {
                Timber.w("H5600: watch busy, retry #$notReady")
                delay(POLL_REPLY_TIMEOUT_MS.milliseconds)
            }
            send(H5600Write(H5600Channel.CONVOY, byteArrayOf(0x00, 0x00, 0x00)))
        }
    }

    /**
     * The setup requests of this read: only the two values the decoder needs. A read starts only after `47 01` (the watch
     * finished its setup), so the watch needs no replay; the old replay of twelve reads cost about 13 s on the slow link and,
     * run before `47 01`, took the connection setup's answers.
     */
    internal fun setupCommands(): List<Int> = QUICK_SETUP_COMMANDS

    /** Sends each one-byte setup request and waits for its reply. Returns the step goal, if read. */
    private suspend fun runSetup(transfer: Transfer): Int? {
        var stepGoal: Int? = null
        val commands = setupCommands()
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("LifeLog: the link is set up (47 01), only 0x43 and 0x28 are read")
        for (command in commands) {
            if (transfer.lost) break
            Timber.d("H5600: setup 0x${command.toString(16)} -> watch")
            setupWaiting = true
            IO.writeCmd(GetSetMode.GET, byteArrayOf(command.toByte()))
            val reply = awaitSetupReply(transfer, command)
            setupWaiting = false
            if (reply == null) {
                Timber.w("H5600: setup 0x${command.toString(16)}: no reply within ${SETUP_REPLY_TIMEOUT_MS} ms")
                continue
            }
            Timber.d("H5600: setup 0x${command.toString(16)} <- ${hex(reply, HEX_PREVIEW_BYTES)}")
            if (command == STEP_GOAL_COMMAND && reply.size >= 3) {
                stepGoal = (reply[1].toInt() and 0xFF) or ((reply[2].toInt() and 0xFF) shl 8)
            }
            if (command in H5600LifeLogSync.ECHO_REPLY_COMMANDS) {
                IO.writeCmd(GetSetMode.SET, org.avmedia.gshockapi.casio.ConnectionSetup.writeBackValue(command, reply))
            }
        }
        return stepGoal
    }

    private suspend fun awaitSetupReply(
        transfer: Transfer,
        command: Int,
    ): ByteArray? =
        withTimeoutOrNull(SETUP_REPLY_TIMEOUT_MS.milliseconds) {
            var reply: ByteArray? = null
            while (reply == null) {
                // Closed by onLinkLost: no reply will come on this link.
                val candidate = transfer.setupReplies.receiveCatching().getOrNull() ?: break
                if (candidate.firstOrNull() == command.toByte()) reply = candidate
            }
            reply
        }

    private fun onNotification(
        channel: H5600Channel,
        hex: String,
    ) {
        val transfer = synchronized(lock) { active } ?: return
        val bytes = bytesOf(hex)
        val (writes, nowDone) =
            synchronized(lock) {
                when (channel) {
                    H5600Channel.CONVOY -> {
                        transfer.convoyReplies.trySend(Unit)
                        transfer.notifications += bytes
                        transfer.bulkCount++
                        if (bytes.firstOrNull() != BULK_PREFIX_BYTE || transfer.bulkCount % BULK_LOG_EVERY == 0) {
                            Timber.d("H5600: CONVOY <- ${hex(bytes, HEX_PREVIEW_BYTES)} (${bytes.size} B, #${transfer.bulkCount})")
                        }
                    }

                    H5600Channel.DRSP -> {
                        Timber.i("H5600: DRSP <- ${hex(bytes, HEX_PREVIEW_BYTES)}")
                        announcement(bytes)?.let { (index, length) ->
                            transfer.announcements[index] = length
                            Timber.i("H5600: announced index 0x${index.toString(16)} length $length")
                        }
                    }

                    H5600Channel.STATUS -> {
                        Timber.i("H5600: STATUS <- ${hex(bytes, HEX_PREVIEW_BYTES)}")
                        if (bytes.firstOrNull() == WATCH_CONDITION_COMMAND.toByte()) transfer.watchCondition = bytes
                        transfer.setupReplies.trySend(bytes)
                    }
                }
                val next = transfer.sync.onMessage(channel, bytes)
                // While polling, the watchdog owns the poll timing. The busy re-polls scheduled by
                // the sync would duplicate it, so they are dropped here.
                val pending = if (transfer.sync.isPolling) next.filter { it.delayMs == 0L } else next
                pending to transfer.sync.isFinished   // capture isFinished; complete() moves below
            }
        scope.launch {
            // Send all echo writes FIRST (e.g. the END echo for 04 2e), then signal
            // completion so request() does not resume and clean up before the write goes out.
            for (write in writes) send(write)
            if (nowDone) transfer.finished.complete(Unit)
        }
    }

    private suspend fun send(write: H5600Write) {
        if (write.delayMs > 0) {
            Timber.d("H5600: waiting ${write.delayMs} ms (watch busy)")
            delay(write.delayMs)
        }
        Timber.i("H5600: ${write.channel} -> ${hex(write.data, HEX_PREVIEW_BYTES)}")
        when (write.channel) {
            H5600Channel.CONVOY -> IO.writeCmd(GetSetMode.CONVOY, write.data)
            H5600Channel.DRSP -> IO.writeCmd(GetSetMode.DATA_REQUEST, write.data)
            H5600Channel.STATUS -> IO.writeCmd(GetSetMode.SET, write.data)
        }
    }

    /** Index announcement `00 2E <index> <length, 3 bytes LE>` on DRSP. */
    private fun announcement(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < ANNOUNCEMENT_BYTES || bytes[0] != ANNOUNCEMENT_COMMAND.toByte() || bytes[1] != 0x2E.toByte()) {
            return null
        }
        if (bytes[2] == 0xFF.toByte()) return null
        val length =
            (bytes[3].toInt() and 0xFF) or
                    ((bytes[4].toInt() and 0xFF) shl 8) or
                    ((bytes[5].toInt() and 0xFF) shl 16)
        return (bytes[2].toInt() and 0xFF) to length
    }

    private fun hex(
        bytes: ByteArray,
        limit: Int,
    ): String {
        val shown = bytes.take(limit).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        return if (bytes.size > limit) "$shown ..." else shown
    }

    internal fun bytesOf(hex: String): ByteArray {
        val intArray = Utils.toIntArray(hex)
        return Utils.byteArrayOfIntArray(intArray.toIntArray())
    }

    private const val BULK_PREFIX_BYTE: Byte = 0x05
}