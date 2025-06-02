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

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<Int>
    }

    suspend fun request(): Int {
        val timerValue = CachedIO.request("18") { key -> getTimer(key) }
        return timerValue
    }

    private suspend fun getTimer(key: String): Int {

        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
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
                    DeferredValueHolder.deferredResult.complete(seconds)
                },
                onFailure = { error ->
                    DeferredValueHolder.deferredResult.completeExceptionally(error)
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
                    // Handle error appropriately
                    println("Failed to set timer: ${error.message}")
                }
            )
    }

    // Pure functions for timer calculations
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

    // Pure encoding/decoding functions
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