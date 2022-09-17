/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gShockPhoneSync.casio

import org.avmedia.gShockPhoneSync.ui.settings.SettingsModel
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.Utils.byteArrayOfIntArray
import org.avmedia.gShockPhoneSync.utils.Utils.byteArrayOfInts
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId

class Casio5600Watch: BluetoothWatch() {

    init {
    }

    override fun init() {
        super.init()
    }

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
                        0x000e,
                        byteArrayOfInts(
                            CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code,
                            index + 1
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
                writeCmd(0x000c, Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code.toByte()))
            }
            "SET_SETTINGS" -> {
                val settings = JSONObject(message).get("value") as JSONObject
                writeCmd(0x000e, SettingsEncoder.encode(settings))
            }
            "SET_TIME" -> {
                val dateTimeMs: Long = JSONObject(message).get("value") as Long

                val dateTime =
                    Instant.ofEpochMilli(dateTimeMs).atZone(ZoneId.systemDefault())
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

    private fun toCasioCmd(bytesStr: String): ByteArray {
        val parts = bytesStr.chunked(2)
        val hexArr = parts.map { str ->
            str.toInt(16).toByte()
        }
        return hexArr.toByteArray()
    }

    override fun getPressedWatchButton(): WATCH_BUTTON {
        /*
        RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
        LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
                      0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
        */
        val bleIntArr = Utils.toIntArray(WatchDataCollector.bleFeaturesValue)
        if (bleIntArr.size < 19) {
            return WATCH_BUTTON.LOWER_LEFT
        }

        return when (bleIntArr[8]) {
            in 0..1 -> WATCH_BUTTON.LOWER_LEFT
            4 -> WATCH_BUTTON.LOWER_RIGHT
            else -> WATCH_BUTTON.INVALID
        }
    }

    override fun isActionButtonPressed(): Boolean {
        val watchButtonPressed = getPressedWatchButton()
        return watchButtonPressed == WATCH_BUTTON.LOWER_RIGHT
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
            CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code -> {
                val intArray = Utils.toIntArray(data)
                if (intArray[1] == 0) {
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
                json.put("CASIO_BLE_FEATURES", data)
            }
        }
        return json
    }
}
