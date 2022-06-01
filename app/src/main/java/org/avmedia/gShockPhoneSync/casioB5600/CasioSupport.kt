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

    enum class WATCH_BUTTON {
        UPPER_LEFT, LOWER_LEFT, UPPER_RIGHT, LOWER_RIGHT, INVALID
    }

    init {
        initHandlesMap()
    }

    fun init() {
        WatchDataCollector.start()
    }

    /*
    I/BleExtensionsKt: Service 00001801-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--
I/BleExtensionsKt: Service 00001800-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--00002a00-0000-1000-8000-00805f9b34fb: READABLE
    |--00002a01-0000-1000-8000-00805f9b34fb: READABLE
I/BleExtensionsKt: Service 00001804-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--00002a07-0000-1000-8000-00805f9b34fb: READABLE
I/BleExtensionsKt: Service 26eb000d-b012-49a8-b1f8-394fb2032b0f
    Characteristics:
    |--26eb002c-b012-49a8-b1f8-394fb2032b0f: WRITABLE WITHOUT RESPONSE
    |--26eb002d-b012-49a8-b1f8-394fb2032b0f: WRITABLE, NOTIFIABLE
    |------00002902-0000-1000-8000-00805f9b34fb: EMPTY
    |--26eb0023-b012-49a8-b1f8-394fb2032b0f: WRITABLE, NOTIFIABLE
    |------00002902-0000-1000-8000-00805f9b34fb: EMPTY
    |--26eb0024-b012-49a8-b1f8-394fb2032b0f: WRITABLE WITHOUT RESPONSE, NOTIFIABLE
    |------00002902-0000-1000-8000-00805f9b34fb: EMPTY
     */

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

    private fun getPressedWatchButton(): WATCH_BUTTON {
        /*
        RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
        LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
        */
        val bleIntArr = Utils.toIntArray(WatchDataCollector.bleFeatures)
        if (bleIntArr.size < 19) {
            return WATCH_BUTTON.LOWER_LEFT
        }

        return when (bleIntArr[8]) {
            1 -> WATCH_BUTTON.LOWER_LEFT
            4 -> WATCH_BUTTON.LOWER_RIGHT
            else -> WATCH_BUTTON.INVALID
        }
    }

    fun isActionButtonPressed (): Boolean {
        val watchButtonPressed = CasioSupport.getPressedWatchButton()
        return watchButtonPressed == CasioSupport.WATCH_BUTTON.LOWER_RIGHT
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
            CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code -> {
                json.put("CASIO_DST_SETTING", data)
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
                json.put("CASIO_WATCH_CONDITION", BatteryLevelDecoder.decodeValue(data))
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
