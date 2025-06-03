/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gshockapi.io

import CachedIO
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import java.util.UUID

object IO {
    private data class State(
        val availableCharacteristics: Map<UUID, BluetoothGattCharacteristic>? = null,
        var writer: ((BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit)? = null
    )

    private var state = State()

    enum class WatchButton {
        UPPER_LEFT, LOWER_LEFT, UPPER_RIGHT, LOWER_RIGHT, NO_BUTTON, FIND_PHONE, ALLAYS_CONNECTED_CONNECTION, INVALID
    }

    enum class DstState(val state: Int) {
        ZERO(0), TWO(2), FOUR(4)
    }

    fun request(request: String) {
        writeCmd(GetSetMode.GET, request)
    }

    fun init() {
        // NO-OP
    }

    fun writeCmd(handle: GetSetMode, bytesArray: ByteArray) {
        Connection.write(handle, bytesArray)
    }

    fun writeCmd(handle: GetSetMode, cmd: String) {
        writeCmdFromString(handle, cmd)
    }

    private fun writeCmdFromString(handle: GetSetMode, bytesStr: String) {
        Connection.write(handle, toCasioCmd(bytesStr))
    }

    private fun toCasioCmd(bytesStr: String): ByteArray =
        bytesStr
            .chunked(2)
            .map { str ->
                runCatching { str.toInt(16).toByte() }
                    .getOrDefault(str.toInt(16).toByte())
            }
            .toByteArray()

    fun removeFromCache(newValue: String) {
        CachedIO.createKey(newValue).let { key ->
            CachedIO.remove(key)
        }
    }
}
