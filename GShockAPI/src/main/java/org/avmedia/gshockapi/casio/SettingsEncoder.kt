/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 11:01 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 11:01 a.m.
 */

package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import kotlin.experimental.or

object SettingsEncoder {
    fun encode(settings: JSONObject): ByteArray {
        val MASK_24_HOURS =         0b00000001
        val MASK_BUTTON_TONE_OFF =  0b00000010
        val MASK_LIGHT_OFF =        0b00000100
        val POWER_SAVING_MODE =     0b00010000

        val arr = ByteArray(12)
        arr [0] = CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()
        if (settings.get("timeFormat") == "24h") {arr [1] = (arr [1] or MASK_24_HOURS.toByte()) }
        if (settings.get("buttonTone") == false) {arr [1] = (arr [1] or MASK_BUTTON_TONE_OFF.toByte()) }
        if (settings.get("autoLight") == false) {arr [1] = (arr [1] or MASK_LIGHT_OFF.toByte()) }
        if (settings.get("powerSavingMode") == false) {arr [1] = (arr [1] or POWER_SAVING_MODE.toByte()) }

        if (settings.get("lightDuration") == "4s") {arr[2] = 1}
        if (settings.get("dateFormat") == "DD:MM") arr[4] = 1

        val language = settings.get("language")
        when (language) {
            "English" -> arr [5] = 0
            "Spanish" -> arr [5] = 1
            "French" -> arr [5] = 2
            "German" -> arr [5] = 3
            "Italian" -> arr [5] = 4
            "Russian" -> arr [5] = 5
        }

        return arr
    }

    fun encodeTimeAdjustment(settings: JSONObject): ByteArray {

        var casioIsAutoTimeOriginalValue = settings.getString("casioIsAutoTimeOriginalValue")
        if (casioIsAutoTimeOriginalValue.isEmpty()) {
            return "".toByteArray()
        }

        // syncing off: 110f0f0f0600500004000100->80<-37d2
        // syncing on:  110f0f0f0600500004000100->00<-37d2

        var intArray = Utils.toIntArray(casioIsAutoTimeOriginalValue)

        if (settings.get("timeAdjustment") == true) {
            intArray[12] = 0x00
        } else {
            intArray[12] = 0x80
        }

        return intArray.foldIndexed(ByteArray(intArray.size)) { i, a, v ->
            a.apply {
                set(
                    i,
                    v.toByte()
                )
            }
        }
    }
}
