package org.avmedia.gshockapi.apiIO

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.getBooleanSafe
import org.json.JSONObject
import timber.log.Timber
import java.util.ArrayList

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
}