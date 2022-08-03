/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gShockPhoneSync.casio

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics.device
import org.json.JSONObject
import java.util.*

sealed class BluetoothWatch {

    private var mAvailableCharacteristics: Map<UUID, BluetoothGattCharacteristic>? = null
    private lateinit var writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit
    private val handlesToCharacteristicsMap = HashMap<Int, UUID>()

    enum class WATCH_BUTTON {
        UPPER_LEFT, LOWER_LEFT, UPPER_RIGHT, LOWER_RIGHT, INVALID
    }

    init {
        initHandlesMap()
    }

    public open fun init() {
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

    protected fun initHandlesMap() {
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

    protected fun writeCmd(handle: Int, bytesArray: ByteArray) {
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

    abstract fun callWriter(message: String)
    abstract fun toJson(data: String): JSONObject
    abstract fun getPressedWatchButton(): WATCH_BUTTON
    abstract fun isActionButtonPressed(): Boolean
}
