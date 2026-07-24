package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Timer Processing Logic
// ============================================================================

/**
 * Pure functional core for timer processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Input -> Deterministic Output transformation.
 */
@RequiresApi(Build.VERSION_CODES.O)
object TimerIOFunctional {
    data class TimerState(
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
        val totalSeconds: Int
    )

    /**
     * Pure function: Convert total seconds to timer state components.
     * 
     * No side effects - simple arithmetic transformation.
     * 
     * @param totalSeconds Total seconds (0 to 23:59:59 = 86399)
     * @return TimerState with hours, minutes, seconds, and total
     */
    fun secondsToTimerState(totalSeconds: Int): TimerState {
        val hours = totalSeconds / 3600
        val minutesAndSeconds = totalSeconds % 3600
        val minutes = minutesAndSeconds / 60
        val seconds = minutesAndSeconds % 60
        return TimerState(hours, minutes, seconds, totalSeconds)
    }

    /**
     * Pure function: Convert timer components to total seconds.
     */
    fun componentsToSeconds(hours: Int, minutes: Int, seconds: Int): Int =
        hours * 3600 + minutes * 60 + seconds

    /**
     * Pure decoder: Decodes hex string to timer state.
     * 
     * No side effects - returns Result for error handling.
     * Buffer structure:
     * [0] - Command code (0x18)
     * [1] - Hours
     * [2] - Minutes
     * [3] - Seconds
     */
    fun decode(data: String): Result<TimerState> = runCatching {
        val timerIntArray = Utils.toIntArray(data)
        val totalSeconds = componentsToSeconds(
            timerIntArray[1],
            timerIntArray[2],
            timerIntArray[3]
        )
        secondsToTimerState(totalSeconds)
    }

    /**
     * Pure encoder: Encodes timer state to byte array.
     * 
     * No side effects - pure transformation.
     * Returns 7-byte array:
     * [0] = 0x18 (command)
     * [1] = hours
     * [2] = minutes
     * [3] = seconds
     * [4..6] = padding
     */
    fun encode(timerState: TimerState): ByteArray =
        ByteArray(7).apply {
            this[0] = 0x18
            this[1] = timerState.hours.toByte()
            this[2] = timerState.minutes.toByte()
            this[3] = timerState.seconds.toByte()
        }

    /**
     * Pure command builder: Creates command to fetch timer from watch.
     */
    fun buildFetchCommand(): ByteArray =
        Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_TIMER.code.toByte())

    /**
     * Pure command builder: Creates command to set timer from JSON message.
     * 
     * Parses and validates input without side effects.
     */
    fun buildSetCommand(message: String): Result<ByteArray> = runCatching {
        JSONObject(message).get("value").toString().toInt()
            .let { secondsToTimerState(it) }
            .let { encode(it) }
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Timer IO handler with state management.
 * 
 * Manages the asynchronous request/response cycle for timer data.
 * Uses pure functional core for all transformations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object TimerIO {
    private data class State(
        val deferredResult: CompletableDeferred<Int>? = null
    )

    private var state = State()

    suspend fun request(): Int =
        CachedIO.request("18") { key -> getTimer(key) }

    private suspend fun getTimer(key: String): Int {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: 0
    }

    fun set(timerValue: Int) {
        fun setFunc() {
            Connection.sendMessage("{action: \"SET_TIMER\", value: $timerValue}")
        }
        CachedIO.set("18") { setFunc() }
    }

    fun onReceived(data: String) {
        // Use pure function to decode
        TimerIOFunctional.decode(data)
            .map { timerState -> timerState.totalSeconds }
            .fold(
                onSuccess = { seconds ->
                    state.deferredResult?.complete(seconds)
                    state = State()
                },
                onFailure = { error ->
                    state.deferredResult?.completeExceptionally(error)
                    state = State()
                }
            )
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // Use pure function to build command, then execute
        IO.writeCmd(
            GetSetMode.GET,
            TimerIOFunctional.buildFetchCommand()
        )
    }

    fun sendToWatchSet(message: String) {
        // Use pure function to build command, then execute
        TimerIOFunctional.buildSetCommand(message)
            .fold(
                onSuccess = { encodedData ->
                    IO.writeCmd(GetSetMode.SET, encodedData)
                },
                onFailure = { error ->
                    Timber.e("Failed to set timer: ${error.message}")
                }
            )
    }
}
