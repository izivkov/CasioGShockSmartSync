package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object WorldCitiesIO {

    suspend fun request(cityNumber: Int): String {
        return ApiIO.request("1f0$cityNumber", ::getWorldCities) as String
    }

    private suspend fun getWorldCities(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_WORLD_CITIES") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)?.complete(data)
        }

        return deferredResult.await()
    }

    fun toJson (data:String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", ApiIO.createKey(data)).put("value", data)
        val characteristicsArray = Utils.toIntArray(data)
        if (characteristicsArray[1] == 0) {
            // 0x1F 00 ... Only the first World City contains the home time.
            // Send this data on topic "HOME_TIME" to be received by HomeTime custom component.
            json.put("HOME_TIME", dataJson)
        }
        json.put("CASIO_WORLD_CITIES", dataJson)
        return json
    }
}