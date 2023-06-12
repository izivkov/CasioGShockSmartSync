package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BatteryLevelDecoder
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object BatteryLevelIO {

    suspend fun request(): String {
        return ApiIO.request("28", ::getBatteryLevel) as String
    }

    private suspend fun getBatteryLevel(key: String): String {

        CasioIO.request(key)

        val deferredResult = CompletableDeferred<String>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_WATCH_CONDITION") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)?.complete(BatteryLevelDecoder.decodeValue(data))
        }

        return deferredResult.await()
    }

    fun toJson (data:String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", ApiIO.createKey(data)).put("value", data)
        json.put("CASIO_WATCH_CONDITION", dataJson)
        return json
    }
}