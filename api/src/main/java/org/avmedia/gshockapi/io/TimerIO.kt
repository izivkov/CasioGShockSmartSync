package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object TimerIO {

    suspend fun request(): Int {
        return CachedIO.request("18", ::getTimer) as Int
    }

    private suspend fun getTimer(key: String): Int {

        CasioIO.request(key)

        fun getTimer(data: String): String {
            return TimerDecoder.decodeValue(data)
        }

        var deferredResult = CompletableDeferred<Int>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("CASIO_TIMER") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            CachedIO.resultQueue.dequeue(key)?.complete(getTimer(data).toInt())
        }

        return deferredResult.await()
    }

    fun set(timerValue: Int) {
        CachedIO.cache.remove("18")
        Connection.sendMessage("{action: \"SET_TIMER\", value: $timerValue}")
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("CASIO_TIMER", dataJson)
        return json
    }

    fun sendToWatch(message: String) {
        CasioIO.writeCmd(
            0x000c,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_TIMER.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        val seconds = JSONObject(message).get("value").toString()
        CasioIO.writeCmd(0x000e, TimerEncoder.encode(seconds))
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