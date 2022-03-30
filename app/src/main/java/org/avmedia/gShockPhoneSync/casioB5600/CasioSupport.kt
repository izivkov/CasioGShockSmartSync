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
import com.google.gson.Gson
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics.device
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.Utils.byteArrayOfInts
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

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

    private fun prepareCurrentTime(date: Date): ByteArray {
        val arr = ByteArray(10)
        val cal = Calendar.getInstance()
        cal.time = date
        val year = cal[Calendar.YEAR]
        arr[0] = (year ushr 0 and 0xff).toByte()
        arr[1] = (year ushr 8 and 0xff).toByte()
        arr[2] = (1 + cal[Calendar.MONTH]).toByte()
        arr[3] = cal[Calendar.DAY_OF_MONTH].toByte()
        arr[4] = cal[Calendar.HOUR_OF_DAY].toByte()
        arr[5] = cal[Calendar.MINUTE].toByte()
        arr[6] = (1 + cal[Calendar.SECOND]).toByte()
        var dayOfWk = (cal[Calendar.DAY_OF_WEEK] - 1).toByte()
        if (dayOfWk.toInt() == 0) dayOfWk = 7
        arr[7] = dayOfWk
        arr[8] = TimeUnit.MILLISECONDS.toSeconds((256 * cal[Calendar.MILLISECOND]).toLong())
            .toInt().toByte()
        arr[9] = 1 // or 0?
        return arr
    }

    fun toJson(command: String): JSONObject {
        val jsonResponse = JSONObject()
        val intArray = Utils.toIntArray(command)
        val alarms = JSONArray()

        when (intArray[0]) {
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code -> {
                intArray.removeAt(0)
                alarms.put(createJsonAlarm(intArray))
                jsonResponse.put("ALARMS", alarms)
            }
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code -> {
                intArray.removeAt(0)
                val multipleAlarms = intArray.chunked(4)
                multipleAlarms.forEach {
                    alarms.put(createJsonAlarm(it as ArrayList<Int>))
                }
                jsonResponse.put("ALARMS", alarms)
            }
            in listOf(
                CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code,
                CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code,
                CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code,
                CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code,
                CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code,
            ) -> {
                jsonResponse.put("WATCH_INFO_DATA", command)
            }
            else -> {
                Timber.d("Unhandled Command........")
            }
        }

        return jsonResponse
    }

    private fun createJsonAlarm(intArray: ArrayList<Int>): JSONObject {
        var alarm = Alarms.Alarm(
            intArray[2],
            intArray[3],
            intArray[0] == 0x40
        )
        val gson = Gson()
        return JSONObject(gson.toJson(alarm))
    }

    fun setWriter(writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit) {
        this.writer = writer
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun callWriter(device: BluetoothDevice, message: String) {
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

            "SET_TIME" -> {
                var dateTimeMs: Long = JSONObject(message).get("value") as Long
                val dateTime: Date = Date(dateTimeMs)

                val timeData = prepareCurrentTime(dateTime)
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
}