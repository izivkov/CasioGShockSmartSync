package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch

object DstWatchStateIO {

    suspend fun request(state: BluetoothWatch.DTS_STATE): String {
        return ApiIO.request("1d0${state.state}", ::getDSTWatchState) as String
    }

    private suspend fun getDSTWatchState(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_DST_WATCH_STATE") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)?.complete(data)
        }

        return deferredResult.await()
    }
}