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

@RequiresApi(Build.VERSION_CODES.O)
object TimerIO {
    data class TimerState(
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
        val totalSeconds: Int
    )

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
        TimerDecoder.decode(data)
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
        IO.writeCmd(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_TIMER.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        runCatching { JSONObject(message).get("value").toString().toInt() }
            .map { seconds -> TimerCalculator.secondsToTimerState(seconds) }
            .map { timerState -> TimerEncoder.encode(timerState) }
            .fold(
                onSuccess = { encodedData ->
                    IO.writeCmd(GetSetMode.SET, encodedData)
                },
                onFailure = { error ->
                    println("Failed to set timer: ${error.message}")
                }
            )
    }

    object TimerCalculator {
        fun secondsToTimerState(totalSeconds: Int): TimerState {
            val hours = totalSeconds / 3600
            val minutesAndSeconds = totalSeconds % 3600
            val minutes = minutesAndSeconds / 60
            val seconds = minutesAndSeconds % 60
            return TimerState(hours, minutes, seconds, totalSeconds)
        }

        fun componentsToSeconds(hours: Int, minutes: Int, seconds: Int): Int =
            hours * 3600 + minutes * 60 + seconds
    }

    object TimerEncoder {
        fun encode(timerState: TimerState): ByteArray =
            ByteArray(7).apply {
                this[0] = 0x18
                this[1] = timerState.hours.toByte()
                this[2] = timerState.minutes.toByte()
                this[3] = timerState.seconds.toByte()
            }
    }

    object TimerDecoder {
        fun decode(data: String): Result<TimerState> = runCatching {
            val timerIntArray = Utils.toIntArray(data)
            val totalSeconds = TimerCalculator.componentsToSeconds(
                timerIntArray[1],
                timerIntArray[2],
                timerIntArray[3]
            )
            TimerCalculator.secondsToTimerState(totalSeconds)
        }
    }
}
