/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gshockapi.io

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.DeviceCharacteristics
import java.util.*

object CasioIO {

    private var mAvailableCharacteristics: Map<UUID, BluetoothGattCharacteristic>? = null
    private lateinit var writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit

    enum class WATCH_BUTTON {
        UPPER_LEFT, LOWER_LEFT, UPPER_RIGHT, LOWER_RIGHT, NO_BUTTON, FIND_PHONE, INVALID
    }

    enum class DTS_STATE(val state: Int) { ZERO(0), TWO(2), FOUR(4) }

    fun request(request: String) {
        writeCmd(0xC, request)
    }

    fun init() {
        Connection.enableNotifications()
    }

    fun setWriter(writer: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit) {
        this.writer = writer
    }

    fun writeCmd(handle: Int, bytesArray: ByteArray) {
        fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
        println("writeCmd: handle: $handle, bytesArray: ${bytesArray.toHexString()}")

        val handle = lookupHandle(handle)
        if (handle == null) {
            ProgressEvents.onNext("ApiError")
            return
        }
        writer.invoke(
            DeviceCharacteristics.device,
            handle,
            bytesArray
        )
    }


    fun writeCmd(handle: Int, cmd: String) {
        println("Sending request: handle: $handle, cmd: $cmd")
        writeCmdFromString(handle, cmd)
    }

    /// new
    fun writeCmdFromString(handle: Int, bytesStr: String) {
        val handle = lookupHandle(handle)
        if (handle == null) {
            ProgressEvents.onNext("ApiError")
            return
        }
        writer.invoke(
            DeviceCharacteristics.device,
            handle,
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

    private fun lookupHandle(handle: Int): BluetoothGattCharacteristic? {
        return DeviceCharacteristics.findCharacteristic(DeviceCharacteristics.handlesToCharacteristicsMap[handle])
    }
}