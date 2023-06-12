package org.avmedia.gshockapi.apiIO

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object SettingsIO {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): Settings {
        return ApiIO.request("GET_SETTINGS", ::getBasicSettings) as Settings
    }

    private suspend fun getBasicSettings(key: String): Settings {
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

    fun toJson(data: String): JSONObject {
        val dataJson = JSONObject().put("key", ApiIO.createKey(data))
            .put("value", decodeToJson(data))
        val settingsJson = JSONObject()
        settingsJson.put("SETTINGS", dataJson)
        return settingsJson
    }

    /*
Time Format:
24 h:   13 05 00 00 00 00 00 00 00 00 00 00
12 h:   13 04 00 00 00 00 00 00 00 00 00 00

Date format:
mm:dd   13 04 00 00 00 00 00 00 00 00 00 00
dd:mm   13 04 00 00 01 00 00 00 00 00 00 00

Languages:
english:13 04 00 00 00 00 00 00 00 00 00 00
spanish:13 04 00 00 00 01 00 00 00 00 00 00
fr:     13 04 00 00 00 02 00 00 00 00 00 00
german: 13 04 00 00 00 03 00 00 00 00 00 00
italian:13 04 00 00 00 04 00 00 00 00 00 00
russian:13 04 00 00 00 05 00 00 00 00 00 00

Button Tone:
on:     13 04 00 00 00 00 00 00 00 00 00 00
off:    13 06 00 00 00 00 00 00 00 00 00 00

Auto Light: ?????
on:     13 00 00 00 00 00 00 00 00 00 00 00
off:    13 04 00 00 00 00 00 00 00 00 00 00

Light Duration:
2s      13 04 00 00 00 00 00 00 00 00 00 00
4s      13 04 01 00 00 00 00 00 00 00 00 00

Power Saving:
on      13 04 00 00 00 00 00 00 00 00 00 00
off     13 14 00 00 00 00 00 00 00 00 00 00

Combination:
auto light: on
Power Saving: off
        13 02 00 00 00 00 00 00 00 00 00 00

24 hours:
        13 03 00 00 00 00 00 00 00 00 00 00

Byte 2 as binary:
24 hours:       00000001
button tone     00000010
light off:      00000100
pwr. saving off:00010000
 */
    private fun decodeToJson(casioArray: String): JSONObject {
        return createJsonSettings(casioArray)
    }

    private fun createJsonSettings(settingString: String): JSONObject {
        val MASK_24_HOURS = 0b00000001
        val MASK_BUTTON_TONE_OFF = 0b00000010
        val MASK_LIGHT_OFF = 0b00000100
        val POWER_SAVING_MODE = 0b00010000

        val settings = Settings()

        val settingArray = Utils.toIntArray(settingString)

        if (settingArray[1] and MASK_24_HOURS != 0) {
            settings.timeFormat = "24h"
        } else {
            settings.timeFormat = "12h"
        }
        settings.buttonTone = settingArray[1] and MASK_BUTTON_TONE_OFF == 0
        settings.autoLight = settingArray[1] and MASK_LIGHT_OFF == 0
        settings.powerSavingMode = settingArray[1] and POWER_SAVING_MODE == 0

        if (settingArray[4] == 1) {
            settings.dateFormat = "DD:MM"
        } else {
            settings.dateFormat = "MM:DD"
        }

        if (settingArray[5] == 0) {
            settings.language = "English"
        }
        if (settingArray[5] == 1) {
            settings.language = "Spanish"
        }
        if (settingArray[5] == 2) {
            settings.language = "French"
        }
        if (settingArray[5] == 3) {
            settings.language = "German"
        }
        if (settingArray[5] == 4) {
            settings.language = "Italian"
        }
        if (settingArray[5] == 5) {
            settings.language = "Russian"
        }

        if (settingArray[2] == 1) {
            settings.lightDuration = "4s"
        } else {
            settings.lightDuration = "2s"
        }

        return JSONObject(Gson().toJson(settings))
    }
}