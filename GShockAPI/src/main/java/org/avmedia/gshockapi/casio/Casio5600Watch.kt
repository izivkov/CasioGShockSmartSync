/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.byteArrayOfIntArray
import org.avmedia.gshockapi.utils.Utils.byteArrayOfInts
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId

class Casio5600Watch : BluetoothWatch() {
    private val settingsSimpleModel = SettingsSimpleModel()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun callWriter(message: String) {
        when (val action = JSONObject(message).get("action")) {
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
                    SettingsDecoder.CasioIsAutoTimeOriginalValue.value
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

    override fun toJson(data: String): JSONObject {
        val intArray = Utils.toIntArray(data)
        val json = JSONObject()
        when (intArray[0]) {
            in listOf(
                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code,
                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code
            ) -> {
                return AlarmDecoder.toJson(data)
            }

            // Add topics so the right component will receive data
            CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code -> {
                json.put("CASIO_DST_SETTING", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code -> {
                return SettingsDecoder.toJson(data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code -> {
                SettingsDecoder.getTimeAdjustment(data, settingsSimpleModel)
                return SettingsDecoder.toJsonTimeAdjustment(settingsSimpleModel)
            }
            CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code -> {
                val reminderJson = JSONObject()
                val startOfData = data+2 // skip command code 0x31 01
                reminderJson.put ("REMINDERS", ReminderDecoder.reminderTimeToJson(startOfData))
                return reminderJson
            }
            CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code -> {
                val reminderJson = JSONObject()
                reminderJson.put ("REMINDERS", ReminderDecoder.reminderTitleToJson(data))
                return reminderJson
            }
            CasioConstants.CHARACTERISTICS.CASIO_TIMER.code -> {
                json.put("CASIO_TIMER", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code -> {
                val characteristicsArray = Utils.toIntArray(data)
                if (characteristicsArray[1] == 0) {
                    // 0x1F 00 ... Only the first World City contains the home time.
                    // Send this data on topic "HOME_TIME" to be received by HomeTime custom component.
                    json.put("HOME_TIME", data)
                }
                json.put("CASIO_WORLD_CITIES", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code -> {
                json.put("CASIO_DST_WATCH_STATE", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code -> {
                json.put("CASIO_WATCH_NAME", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code -> {
                json.put("CASIO_WATCH_CONDITION", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_APP_INFORMATION.code -> {
                json.put("CASIO_APP_INFORMATION", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code -> {
                json.put("BUTTON_PRESSED", data)
            }
        }
        return json
    }
}
