/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:34 p.m.
 */

package org.avmedia.gshockapi.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gshockapi.casio.CasioConstants
import java.util.*

object DeviceCharacteristics {

    lateinit var device: BluetoothDevice

    val handlesToCharacteristicsMap by lazy(::initHandlesMap)

    private val characteristicMap by lazy {
        characteristics.associateBy { it.uuid }.toMap()
    }

    fun init(device: BluetoothDevice) {
        this.device = device
    }

    private val characteristics by lazy {
        Connection.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    fun findCharacteristic(uuid: UUID?): BluetoothGattCharacteristic? {
        return characteristicMap[uuid]
    }

    fun printCharacteristics() {
        println(characteristicMap)
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

Here us a table of standard handles and corresponding UUID:
handle: 0x0003, char properties: 0x02, char value handle: 0x0004, uuid: 00002a00-0000-1000-8000-00805f9b34fb
handle: 0x0005, char properties: 0x02, char value handle: 0x0006, uuid: 00002a01-0000-1000-8000-00805f9b34fb
handle: 0x0008, char properties: 0x02, char value handle: 0x0009, uuid: 00002a07-0000-1000-8000-00805f9b34fb
handle: 0x000b, char properties: 0x04, char value handle: 0x000c, uuid: 26eb002c-b012-49a8-b1f8-394fb2032b0f
handle: 0x000d, char properties: 0x18, char value handle: 0x000e, uuid: 26eb002d-b012-49a8-b1f8-394fb2032b0f
handle: 0x0010, char properties: 0x18, char value handle: 0x0011, uuid: 26eb0023-b012-49a8-b1f8-394fb2032b0f
handle: 0x0013, char properties: 0x14, char value handle: 0x0014, uuid: 26eb0024-b012-49a8-b1f8-394fb2032b0f
 */

    private fun initHandlesMap(): HashMap<Int, UUID> {
        var handlesMap = HashMap<Int, UUID>()

        handlesMap[0x04] = CasioConstants.CASIO_GET_DEVICE_NAME
        handlesMap[0x06] = CasioConstants.CASIO_APPEARANCE
        handlesMap[0x09] = CasioConstants.TX_POWER_LEVEL_CHARACTERISTIC_UUID
        handlesMap[0x0c] =
            CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID
        handlesMap[0x0e] = CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID
        handlesMap[0x11] = CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID
        handlesMap[0x14] = CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID

        handlesMap[0xFF] = CasioConstants.SERIAL_NUMBER_STRING

        return handlesMap
    }
}
