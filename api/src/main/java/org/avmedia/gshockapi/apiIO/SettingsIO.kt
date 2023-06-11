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
import org.json.JSONObject
import timber.log.Timber
import java.util.ArrayList

object SettingsIO {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): Settings {
        return ApiIO.request("GET_SETTINGS", ::getBasicSettings) as Settings
    }

    private suspend fun getBasicSettings(key:String): Settings {
        Connection.sendMessage("{ action: '$key'}")

        val key = "13"
        var deferredResult = CompletableDeferred<Settings>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("SETTINGS") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")
            val model = Gson().fromJson(data, Settings::class.java)
            ApiIO.resultQueue.dequeue(key)?.complete(model)
        }
        return deferredResult.await()
    }

    fun set(settings: Settings) {
        val settingJson = Gson().toJson(settings)
        ApiIO.cache.remove("GET_SETTINGS")
        Connection.sendMessage("{action: \"SET_SETTINGS\", value: ${settingJson}}")
    }
}