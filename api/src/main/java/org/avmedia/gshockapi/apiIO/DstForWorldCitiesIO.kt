package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.json.JSONObject

object DstForWorldCitiesIO {

    suspend fun request(cityNumber: Int): String {
        return ApiIO.request("1e0$cityNumber", ::getDSTForWorldCities) as String
    }

    private suspend fun getDSTForWorldCities(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_DST_SETTING") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)?.complete(data)
        }

        return deferredResult.await()
    }
}