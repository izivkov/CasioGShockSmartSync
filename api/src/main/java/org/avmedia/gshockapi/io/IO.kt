/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gshockapi.io

import CachedIO
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.WatchLink
import java.util.UUID

// ============================================================================
// Pure Functional Core: Command Conversion
// ============================================================================

/**
 * Pure functional core for IO command processing.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles protocol-level command conversion and formatting.
 */
object IOFunctional {
    /**
     * Pure converter: Converts hex string to Casio command byte array.
     *
     * Takes a hex string (e.g., "1e05"), splits into pairs, converts each pair
     * to a byte. Uses getOrDefault to handle invalid hex gracefully.
     * No side effects - pure transformation.
     */
    fun toCasioCmd(bytesStr: String): ByteArray =
        bytesStr
            .chunked(2)
            .map { str ->
                runCatching { str.toInt(16).toByte() }
                    .getOrDefault(str.toInt(16).toByte())
            }
            .toByteArray()
}

object IO {
    private data class State(
        val availableCharacteristics: Map<UUID, BluetoothGattCharacteristic>? = null,
        var writer: ((BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit)? = null,
    )

    private var state = State()

    enum class WatchButton {
        UPPER_LEFT,
        LOWER_LEFT,
        UPPER_RIGHT,
        LOWER_RIGHT,
        NO_BUTTON,
        FIND_PHONE,
        ALLAYS_CONNECTED_CONNECTION,
        INVALID,
    }

    enum class DstState(val state: Int) {
        ZERO(0),
        TWO(2),
        FOUR(4),
    }

    fun request(request: String) {
        writeCmd(GetSetMode.GET, request)
    }

    fun init() {
        // NO-OP
    }

    fun writeCmd(
        handle: GetSetMode,
        bytesArray: ByteArray,
    ) {
        WatchLink.write(handle, bytesArray)
    }

    /** Waits for the result: true when the write result came (the watch took the frame). */
    suspend fun writeChecked(
        handle: GetSetMode,
        bytesArray: ByteArray,
    ): Boolean = WatchLink.writeChecked(handle, bytesArray)

    fun writeCmd(
        handle: GetSetMode,
        cmd: String,
    ) {
        writeCmdFromString(handle, cmd)
    }

    private fun writeCmdFromString(
        handle: GetSetMode,
        bytesStr: String,
    ) {
        WatchLink.write(handle, IOFunctional.toCasioCmd(bytesStr))
    }

    fun removeFromCache(newValue: String) {
        CachedIO.createKey(newValue).let { key ->
            CachedIO.remove(key)
        }
    }
}
