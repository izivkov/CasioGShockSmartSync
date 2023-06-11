package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
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
}