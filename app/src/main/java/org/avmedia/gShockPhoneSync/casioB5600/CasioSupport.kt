/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gShockPhoneSync.casioB5600

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics.device
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.Utils.byteArrayOfIntArray
import org.avmedia.gShockPhoneSync.utils.Utils.byteArrayOfInts
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object CasioSupport {

    private var mAvailableCharacteristics: Map<UUID, BluetoothGattCharacteristic>? = null
    private lateinit var writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit
    private val handlesToCharacteristicsMap = HashMap<Int, UUID>()

    init {
        initHandlesMap()
    }

    fun init() {
        WatchDataCollector.start()
    }

    private fun initHandlesMap() {
        handlesToCharacteristicsMap[0x04] = CasioConstants.CASIO_GET_DEVICE_NAME
        handlesToCharacteristicsMap[0x06] = CasioConstants.CASIO_APPEARANCE
        handlesToCharacteristicsMap[0x09] = CasioConstants.TX_POWER_LEVEL_CHARACTERISTIC_UUID
        handlesToCharacteristicsMap[0x0c] =
            CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID
        handlesToCharacteristicsMap[0x0e] = CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID
        handlesToCharacteristicsMap[0x11] = CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID
        handlesToCharacteristicsMap[0x14] = CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID
    }

    fun setWriter(writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit) {
        this.writer = writer
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callWriter(message: String) {
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

            "SET_TIME" -> {
                var dateTimeMs: Long = JSONObject(message).get("value") as Long

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

    private fun writeCmd(handle: Int, bytesArray: ByteArray) {
        writer.invoke(
            device,
            lookupHandle(handle),
            bytesArray
        )
    }

    fun writeCmdFromString(handle: Int, bytesStr: String) {
        writer.invoke(
            device,
            lookupHandle(handle),
            toCasioCmd(bytesStr)
        )
    }

    private fun toCasioCmd(bytesStr: String): ByteArray {
        val parts = bytesStr.chunked(2)
        val hexArr = parts.map { str ->
            str.toInt(16).toByte()
        }
        return hexArr.toByteArray()
    }

    private val characteristicMap by lazy {
        DeviceCharacteristics.characteristics.associateBy { it.uuid }.toMap()
    }

    private fun lookupHandle(handle: Int): BluetoothGattCharacteristic {
        return DeviceCharacteristics.findCharacteristic(handlesToCharacteristicsMap[handle])
    }

    fun toJson(data: String): JSONObject {
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
            CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code -> {json.put("CASIO_DST_SETTING", data)}
            CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code -> {
                val intArray = Utils.toIntArray(data)
                if (intArray[1] == 0) {
                    // 0x1F 00 ... Only the first World City contains the home time.
                    // Send this data on topic "HOME_TIME" to be received by HomeTime custom component.
                    json.put("HOME_TIME", data)
                }
                json.put("CASIO_WORLD_CITIES", data)
            }
            CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code -> {json.put("CASIO_DST_WATCH_STATE", data)}
            CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code -> {json.put("CASIO_WATCH_NAME", data)}
            CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code -> {json.put("CASIO_WATCH_CONDITION", BatteryLevelDecoder.decodeValue(data))}
        }

        return json
    }

    fun requestWatchName() {
        writeCmd(0xC, byteArrayOfInts(0x23))
    }

    fun requestBatteryLevel() {
        writeCmd(0xC, byteArrayOfInts(0x28))
    }

    fun requestHomeTime() {
        writeCmd(0xC, byteArrayOfInts(0x1f, 0x0))
    }
}
