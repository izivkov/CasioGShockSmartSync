/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-27, 9:45 a.m.
 */

package org.avmedia.gshockapi.casio

import com.google.gson.Gson
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONArray
import org.json.JSONObject

object Alarms {

    private const val HOURLY_CHIME_MASK = 0b10000000
    const val ENABLED_MASK =      0b01000000

    private const val ALARM_CONSTANT_VALUE = 0x40

    class Alarm(val hour: Int, val minute: Int, val enabled: Boolean, val hasHourlyChime:Boolean)

    fun fromJsonAlarmFirstAlarm(alarmJson: JSONObject): ByteArray {
        val gson = Gson()
        val alarm: Alarm = gson.fromJson(alarmJson.toString(), Alarm::class.java)

        return createFirstAlarm(alarm)
    }

    private fun createFirstAlarm(alarm: Alarm): ByteArray {

        var flag = 0
        if (alarm.enabled) flag = flag or ENABLED_MASK
        if (alarm.hasHourlyChime) flag = flag or HOURLY_CHIME_MASK

        return Utils.byteArrayOfInts(
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code,
            flag,
            ALARM_CONSTANT_VALUE,
            alarm.hour,
            alarm.minute
        )
    }

    fun fromJsonAlarmSecondaryAlarms(alarmsJson: JSONArray): ByteArray {
        if (alarmsJson.length() < 2) {
            return ByteArray(0)
        }
        val gson = Gson()
        val allAlarms: Array<Alarm> = gson.fromJson(alarmsJson.toString(), Array<Alarm>::class.java)
        // ignore the first alarm
        val alarms = allAlarms.toList().subList(1, allAlarms.size)
        return createSecondaryAlarm(alarms)
    }

    private fun createSecondaryAlarm(alarms: List<Alarm>): ByteArray {
        var allAlarms =
            Utils.byteArrayOfInts(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code)

        for (alarm in alarms) {
            var flag = 0
            if (alarm.enabled) flag = flag or ENABLED_MASK
            if (alarm.hasHourlyChime) flag = flag or HOURLY_CHIME_MASK

            allAlarms += Utils.byteArrayOfInts(
                flag,
                ALARM_CONSTANT_VALUE,
                alarm.hour,
                alarm.minute
            )
        }

        return allAlarms
    }
}