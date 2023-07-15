package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

object DstWatchStateIO {

    suspend fun request(state: CasioIO.DTS_STATE): String {
        return CachedIO.request("1d0${state.state}", ::getDSTWatchState) as String
    }

    private suspend fun getDSTWatchState(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("CASIO_DST_WATCH_STATE") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            CachedIO.resultQueue.dequeue(key)?.complete(data)
        }

        return deferredResult.await()
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("CASIO_DST_WATCH_STATE", dataJson)
        return json
    }
}