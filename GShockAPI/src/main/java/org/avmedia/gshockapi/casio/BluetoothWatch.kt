/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gshockapi.casio

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gshockapi.ble.DeviceCharacteristics
import org.avmedia.gshockapi.ble.DeviceCharacteristics.device
import org.json.JSONObject
import java.util.*

sealed class BluetoothWatch {

    private var mAvailableCharacteristics: Map<UUID, BluetoothGattCharacteristic>? = null
    private lateinit var writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit

    enum class WATCH_BUTTON {
        UPPER_LEFT, LOWER_LEFT, UPPER_RIGHT, LOWER_RIGHT, NO_BUTTON, INVALID
    }

    enum class DTS_STATE(val state: Int) { ZERO(0), TWO(2), FOUR(4) }

    open fun init() {
        CasioIO.init()
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
            try {
                str.toInt(16).toByte()
            } catch (e: java.lang.NumberFormatException) {
                str.toInt(16).toByte()
            }
        }
        return hexArr.toByteArray()
    }

    private fun lookupHandle(handle: Int): BluetoothGattCharacteristic {
        return DeviceCharacteristics.findCharacteristic(DeviceCharacteristics.handlesToCharacteristicsMap[handle])
    }

    abstract fun callWriter(message: String)
    abstract fun toJson(data: String): JSONObject
}
