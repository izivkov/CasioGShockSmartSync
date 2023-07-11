package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

object DstForWorldCitiesIO {

    suspend fun request(cityNumber: Int): String {
        return CachedIO.request("1e0$cityNumber", ::getDSTForWorldCities) as String
    }

    private suspend fun getDSTForWorldCities(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("CASIO_DST_SETTING") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            CachedIO.resultQueue.dequeue(key)?.complete(data)
        }

        return deferredResult.await()
    }


    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("CASIO_DST_SETTING", dataJson)
        return json
    }
}