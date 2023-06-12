package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object WatchNameIO {

    suspend fun request(): String {
        return ApiIO.request("23", ::getWatchName) as String
    }

    private suspend fun getWatchName(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_WATCH_NAME") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)
                ?.complete(Utils.trimNonAsciiCharacters(Utils.toAsciiString(data, 1)))
        }

        return deferredResult.await()
    }

    fun toJson (data:String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", ApiIO.createKey(data)).put("value", data)
        json.put("CASIO_WATCH_NAME", dataJson)
        return json
    }
}