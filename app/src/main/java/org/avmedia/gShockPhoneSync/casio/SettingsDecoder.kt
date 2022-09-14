/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 10:57 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 10:57 a.m.
 */

package org.avmedia.gShockPhoneSync.casio

import com.google.gson.Gson
import org.json.JSONObject

/*
Time Format:
24 h:   130500000000000000000000
12 h:   130400000000000000000000

Date format:
mm:dd   130400000000000000000000
dd:mm   130400000100000000000000

Languages:
english:130400000003000000000000
spanish:130400000001000000000000
fr:     130400000002000000000000
german: 130400000003000000000000
italian:130400000003000000000000
russian:130400000003000000000000

Button Tone:
on:     130400000000000000000000
off:    130600000000000000000000

Auto Light: ?????
on:     130000000000000000000000
off:    130400000000000000000000

Light Duration:
2s      130400000000000000000000
4s      130401000000000000000000

Power Saving:
on      130400000000000000000000
off     131400000000000000000000


Combination:
auto light: on
Power Saving: off
        130200000000000000000000

24 hours:
        130300000000000000000000

Byte 4 as binary:
24 hours:   0000001
button tone 0000010
light off:  0000100
 */

object SettingsDecoder {

    fun toJson(casioArray: String): JSONObject {
        val jsonResponse = JSONObject()
        jsonResponse.put("SETTINGS", createJsonSettings(casioArray))
        return jsonResponse
    }

    private fun createJsonSettings(settingString: String): JSONObject {
        val settings = Settings()

        // INZ TEST
        settings.timeFormat = "12h"
        settings.dateFormat = "MM:DD"
        settings.language = "Italian"
        settings.autoLight = true
        settings.lightDuration = "4s"
        settings.powerSavingMode = false
        settings.timeTone = true
        // INZ TEST END

        val gson = Gson()
        return JSONObject(gson.toJson(settings))
    }
}
