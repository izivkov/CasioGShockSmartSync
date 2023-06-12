package org.avmedia.gshockapi.apiIO

import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.getBooleanSafe
import org.json.JSONObject

object TimeAdjustmentIO {

    suspend fun request(): Boolean {
        return ApiIO.request("GET_TIME_ADJUSTMENT", ::getTimeAdjustment) as Boolean
    }

    private suspend fun getTimeAdjustment(key: String): Boolean {
        Connection.sendMessage("{ action: '$key'}")

        val key = "11"
        var deferredResult = CompletableDeferred<Boolean>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("TIME_ADJUSTMENT") { keyedData ->

            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            val dataJson = JSONObject(data)
            val timeAdjustment = dataJson.getBooleanSafe("timeAdjustment") == true

            ApiIO.resultQueue.dequeue(key)?.complete(timeAdjustment)
        }

        return deferredResult.await()
    }

    fun set(settings: Settings) {
        val settingJson = Gson().toJson(settings)
        ApiIO.cache.remove("TIME_ADJUSTMENT")
        Connection.sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: ${settingJson}}")
    }

    fun toJson(data: String): JSONObject {
        val timeAdjustmentSet = isTimeAdjustmentSet(data)

        val valueJson = toJsonTimeAdjustment(timeAdjustmentSet)
        val dataJson = JSONObject().apply {
            put("key", ApiIO.createKey(data))
            put("value", valueJson)
        }

        CasioIsAutoTimeOriginalValue.value = data

        return JSONObject().apply {
            put("TIME_ADJUSTMENT", dataJson)
        }
    }

    fun isTimeAdjustmentSet(data: String): Boolean {
        // syncing off: 110f0f0f0600500004000100->80<-37d2
        // syncing on:  110f0f0f0600500004000100->00<-37d2

        CasioIsAutoTimeOriginalValue.value = data // save original data for future use
        return Utils.toIntArray(data)[12] == 0
    }

    fun toJsonTimeAdjustment(isTimeAdjustmentSet: Boolean): JSONObject {
        return JSONObject("{\"timeAdjustment\": ${isTimeAdjustmentSet} }")
    }

    object CasioIsAutoTimeOriginalValue {
        var value = ""
    }
}