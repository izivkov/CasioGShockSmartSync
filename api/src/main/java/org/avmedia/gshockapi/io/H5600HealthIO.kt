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
/** Peek test: when true, a peek read does not echo END. Set to false to restore the normal transfer. */
private const val TEST_PEEK_SKIPS_END = true

object H5600HealthIO {
    private const val SETUP_REPLY_TIMEOUT_MS = 5_000L
    private const val OVERALL_TIMEOUT_MS = 180_000L
    private const val STEP_GOAL_COMMAND = 0x43
    private const val WATCH_CONDITION_COMMAND = 0x28
    private const val ANNOUNCEMENT_COMMAND = 0x00
    private const val ANNOUNCEMENT_BYTES = 6
    private const val BULK_LOG_EVERY = 50
    private const val HEX_PREVIEW_BYTES = 16
    private const val POLL_REPLY_TIMEOUT_MS = 1_000L
    private const val POLL_RETRIES_PER_ATTEMPT = 5
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
    }

    private val lock = Any()
    private var active: Transfer? = null

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

    private class AttemptOutcome(val data: StepCounterData, val restart: Boolean)

    /**
     * Runs the LifeLog transfer. If the watch stops answering an attempt, that attempt is restarted from
     * the setup, up to [MAX_TRANSFER_ATTEMPTS] times, and [status] says so.
     */
    suspend fun request(peek: Boolean = true): StepCounterData {
        if (!WatchInfo.hasStepCounter) {
            Timber.i("H5600: step counter not supported on model ${WatchInfo.model}")
            return StepCounterData.unavailable()
        }
        val started =
            synchronized(lock) {
                if (requestRunning) {
                    false
                } else {
                    requestRunning = true
                    true
                }
            }
        if (!started) {
            Timber.w("H5600: request ignored, a transfer is already running")
            return StepCounterData.unavailable()
        }
        lastError = null
        try {
            for (attempt in 1..MAX_TRANSFER_ATTEMPTS) {
                _status.value =
                    if (attempt == 1) {
                        null
                    } else {
                        "LifeLog data failed, trying again (attempt $attempt of $MAX_TRANSFER_ATTEMPTS)"
                    }
                val outcome = runAttempt(ZoneId.systemDefault(), peek)
                if (!outcome.restart) return outcome.data
                Timber.w("H5600: the watch did not answer; attempt $attempt of $MAX_TRANSFER_ATTEMPTS failed")
            }
            lastError =
                "LifeLog data could not be read after $MAX_TRANSFER_ATTEMPTS tries. Open the Health screen to try again."
            Timber.e("H5600: giving up after $MAX_TRANSFER_ATTEMPTS attempts")
            return StepCounterData.unavailable()
        } finally {
            _status.value = null
            synchronized(lock) { requestRunning = false }
        }
    }

    /** One attempt: setup, START, polls, then the data. restart = true when the watch stopped answering. */
    private suspend fun runAttempt(zone: ZoneId, peek: Boolean): AttemptOutcome {
        // Peek test: with peek, END is not echoed, so the watch may keep the data it has not yet been told it can drop.
        val transfer = Transfer(H5600LifeLogSync(echoEnd = !(peek && TEST_PEEK_SKIPS_END)), zone)
        synchronized(lock) { active = transfer }
        try {
            Timber.i("H5600: LifeLog attempt start (peek=$peek, zone=$zone)")
            val stepGoal = runSetup(transfer)
            Timber.i("H5600: setup finished, step goal=$stepGoal")

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
                val stream = H5600HealthDecoder.lifeLogStream(snapshot.first)
                Timber.w("H5600: no LifeLog header in ${stream.size} stream bytes; first bytes ${hex(stream, 20)}")
                return AttemptOutcome(StepCounterData.unavailable(), restart = false)
            }
            Timber.i(
                "H5600: decoded steps=${decoded.currentDaySteps} goal=$stepGoal " +
                        "hours=${decoded.hourlyByHour.withIndex().filter { (it.value ?: 0) > 0 }.map { it.index }} " +
                        "warnings=${decoded.warnings.size}",
            )
            return AttemptOutcome(decoded, restart = false)
        } finally {
            synchronized(lock) { active = null }
            transfer.setupReplies.close()
            Timber.i("H5600: attempt end")
        }
    }

    /**
     * Polls the watch until it reports ready. A poll that gets no reply, and a poll that gets a busy reply
     * (00 01 xx), both count as not ready. After [POLL_RETRIES_PER_ATTEMPT] such polls in a row, the
     * attempt is marked for restart.
     */
    private suspend fun keepPolling(transfer: Transfer) {
        var notReady = 0
        while (!transfer.finished.isCompleted) {
            val reply = withTimeoutOrNull(POLL_REPLY_TIMEOUT_MS.milliseconds) { transfer.convoyReplies.receive() }
            if (!synchronized(lock) { transfer.sync.isPolling }) break
            notReady++
            if (notReady > POLL_RETRIES_PER_ATTEMPT) {
                Timber.w("H5600: watch not ready after $POLL_RETRIES_PER_ATTEMPT polls; restarting the attempt")
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

    /** Sends each one-byte setup request and waits for its reply. Returns the step goal, if read. */
    private suspend fun runSetup(transfer: Transfer): Int? {
        var stepGoal: Int? = null
        for (command in H5600LifeLogSync.SETUP_COMMANDS) {
            Timber.d("H5600: setup 0x${command.toString(16)} -> watch")
            IO.writeCmd(GetSetMode.GET, byteArrayOf(command.toByte()))
            val reply = awaitSetupReply(transfer, command)
            if (reply == null) {
                Timber.w("H5600: setup 0x${command.toString(16)}: no reply within ${SETUP_REPLY_TIMEOUT_MS} ms")
                continue
            }
            Timber.d("H5600: setup 0x${command.toString(16)} <- ${hex(reply, HEX_PREVIEW_BYTES)}")
            if (command == STEP_GOAL_COMMAND && reply.size >= 3) {
                stepGoal = (reply[1].toInt() and 0xFF) or ((reply[2].toInt() and 0xFF) shl 8)
            }
            if (command in H5600LifeLogSync.ECHO_REPLY_COMMANDS) {
                IO.writeCmd(GetSetMode.SET, reply)
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
                val candidate = transfer.setupReplies.receive()
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

    private fun bytesOf(hex: String): ByteArray {
        val intArray = Utils.toIntArray(hex)
        return Utils.byteArrayOfIntArray(intArray.toIntArray())
    }

    private const val BULK_PREFIX_BYTE: Byte = 0x05
}