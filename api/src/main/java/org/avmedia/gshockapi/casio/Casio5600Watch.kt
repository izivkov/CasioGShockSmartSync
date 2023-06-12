/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.apiIO.*
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.byteArrayOfIntArray
import org.avmedia.gshockapi.utils.Utils.byteArrayOfInts
import org.avmedia.gshockapi.utils.Utils.hexToBytes
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.*

class Casio5600Watch : BluetoothWatch() {
    private val settings = Settings()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun callWriter(message: String) {
        when (val action = JSONObject(message).get("action")) {
            "RESET_HAND" -> {
                writeCmd(
                    0x000e,
                    "1a0412000000".hexToBytes()
                )
                writeCmd(
                    0x000e,
                    "1a0418080700".hexToBytes()
                )

                // Adjustment
                // "1a0418->0a<-0700"

                // Minutes
                // 0->15 : 05
                // 15->70 : 07
                // 30->45 : 05
                // 45->0 : 07
                // "1a04180a >07< 00"

                // reset
                // 1a0412000000
                // 1a0418a00500
                // or
                // 1a0418080700  - 9:30 am

                // Counter CW
                // 1a0418a10500
                // 1a0419a00500

                // Counter CW
                // 1a0418090700
                // 1a0400000000

                // Counter CW x5
                // 1a04180d0700
                // 1a0400000000

                // CW
                // 1a04189f0500
                // 1a0419a00500

                // CW x3
                // 1a0418050700
                // 1a0400000000
            }
            "GET_ALARMS" -> {
                // get alarm 1
                writeCmd(
                    0x000c,
                    Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte())
                )

                // get the rest of the alarms
                writeCmd(
                    0x000c,
                    Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code.toByte())
                )
            }
            "SET_ALARMS" -> {
                val alarmsJsonArr: JSONArray = JSONObject(message).get("value") as JSONArray
                val alarmCasio0 = Alarms.fromJsonAlarmFirstAlarm(alarmsJsonArr[0] as JSONObject)
                writeCmd(0x000e, alarmCasio0)
                var alarmCasio: ByteArray = Alarms.fromJsonAlarmSecondaryAlarms(alarmsJsonArr)
                writeCmd(0x000e, alarmCasio)
            }
            "SET_REMINDERS" -> {
                val remindersJsonArr: JSONArray = JSONObject(message).get("value") as JSONArray
                (0 until remindersJsonArr.length()).forEachIndexed { index, element ->
                    val reminderJson = remindersJsonArr.getJSONObject(element)
                    val title = ReminderEncoder.reminderTitleFromJson(reminderJson)
                    writeCmd(
                        0x000e, byteArrayOfInts(
                            CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code, index + 1
                        ) + title
                    )

                    var reminderTime = IntArray(0)
                    reminderTime += CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code
                    reminderTime += index + 1
                    reminderTime += ReminderEncoder.reminderTimeFromJson(reminderJson)
                    writeCmd(0x000e, byteArrayOfIntArray(reminderTime))
                }

                Timber.i("Got reminders $remindersJsonArr")
            }

            "GET_SETTINGS" -> {
                writeCmd(
                    0x000c,
                    Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte())
                )
            }
            "SET_SETTINGS" -> {
                val settings = JSONObject(message).get("value") as JSONObject
                writeCmd(0x000e, SettingsEncoder.encode(settings))
            }

            "GET_TIME_ADJUSTMENT" -> {
                writeCmd(
                    0x000c,
                    Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code.toByte())
                )
            }
            "SET_TIME_ADJUSTMENT" -> {
                val settings = JSONObject(message).get("value") as JSONObject
                // add the original string from Casio, so we do not mess up any ot the other settings.
                settings.put(
                    "casioIsAutoTimeOriginalValue",
                    TimeAdjustmentIO.CasioIsAutoTimeOriginalValue.value
                )
                val encodedTimeAdj = SettingsEncoder.encodeTimeAdjustment(settings)
                if (encodedTimeAdj.isNotEmpty()) {
                    writeCmd(0x000e, encodedTimeAdj)
                }
            }

            "GET_TIMER" -> {
                writeCmd(
                    0x000c,
                    Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_TIMER.code.toByte())
                )
            }
            "SET_TIMER" -> {
                val seconds = JSONObject(message).get("value").toString()
                writeCmd(0x000e, TimerEncoder.encode(seconds))
            }
            "SET_TIME" -> {
                val dateTimeMs: Long = JSONObject(message).get("value") as Long

                val dateTime = Instant.ofEpochMilli(dateTimeMs).atZone(ZoneId.systemDefault())
                    .toLocalDateTime()

                val timeData = TimeEncoder.prepareCurrentTime(dateTime)
                var timeCommand =
                    byteArrayOfInts(CasioConstants.CHARACTERISTICS.CASIO_CURRENT_TIME.code) + timeData

                writeCmd(0x000e, timeCommand)
            }
            else -> {
                Timber.i("callWriter: Unhandled command $action")
            }
        }
    }

    private val toJsonConverters = mapOf<Int, (String) -> JSONObject>(
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code to AlarmsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code to AlarmsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code to DstForWorldCitiesIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code to EventsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code to EventsIO::toJsonTitle,
        CasioConstants.CHARACTERISTICS.CASIO_TIMER.code to TimerIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code to WorldCitiesIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code to DstWatchStateIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code to WatchNameIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code to BatteryLevelIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_APP_INFORMATION.code to AppInfoIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code to ButtonPressedIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code to SettingsIO::toJson,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code to TimeAdjustmentIO::toJson,
    )

    override fun toJson(data: String): JSONObject {
        val intArray = Utils.toIntArray(data)
        val key = intArray[0]
        return toJsonConverters[key]!!.invoke(data)
    }
}
