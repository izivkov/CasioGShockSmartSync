package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GET_SET_MODE
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.O)
object TimerIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<Int>
    }

    suspend fun request(): Int {
        val timerValue = CachedIO.request("18", TimerIO::getTimer)
        return timerValue as Int
    }

    private suspend fun getTimer(key: String): Int {

        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun set(timerValue: Int) {
        fun setFunc () {Connection.sendMessage("{action: \"SET_TIMER\", value: $timerValue}")}
        CachedIO.set("18", ::setFunc)
    }

    fun onReceived(data: String) {
        val decoded = TimerDecoder.decodeValue(data)
        val decodedInt = decoded.toInt()
        DeferredValueHolder.deferredResult.complete(decodedInt)
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        IO.writeCmd(
            GET_SET_MODE.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_TIMER.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        val seconds = JSONObject(message).get("value").toString()
        IO.writeCmd(GET_SET_MODE.SET, TimerEncoder.encode(seconds))
    }

    object TimerDecoder {

        fun decodeValue(data: String): String {
            val timerIntArray = Utils.toIntArray(data)

            val hours = timerIntArray[1]
            val minutes = timerIntArray[2]
            val seconds = timerIntArray[3]

            val inSeconds = hours * 3600 + minutes * 60 + seconds
            return inSeconds.toString()
        }
    }

    object TimerEncoder {
        fun encode(secondsStr: String): ByteArray {
            val inSeconds = secondsStr.toInt()
            val hours = inSeconds / 3600
            val minutesAndSeconds = inSeconds % 3600
            val minutes = minutesAndSeconds / 60
            val seconds = minutesAndSeconds % 60

            val arr = ByteArray(7)
            arr[0] = 0x18
            arr[1] = hours.toByte()
            arr[2] = minutes.toByte()
            arr[3] = seconds.toByte()

            return arr
        }
    }
}