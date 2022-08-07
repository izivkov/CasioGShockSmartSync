/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:34 p.m.
 */

package org.avmedia.gShockPhoneSync.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gShockPhoneSync.casio.BluetoothWatch
import org.avmedia.gShockPhoneSync.casio.CasioConstants
import org.avmedia.gShockPhoneSync.casio.WatchFactory
import timber.log.Timber
import java.util.HashMap
import java.util.UUID

object DeviceCharacteristics {

    lateinit var device: BluetoothDevice

    val handlesToCharacteristicsMap by lazy (::initHandlesMap)

    private val characteristicMap by lazy {
        characteristics.associateBy { it.uuid }.toMap()
    }

    fun init(device: BluetoothDevice) {
        this.device = device
    }

    val characteristics by lazy {
        Connection.servicesOnDevice(device)?.flatMap { service ->
            Timber.i("servicesOnDevice ${service.uuid} ...")
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    fun findCharacteristic(uuid: UUID?): BluetoothGattCharacteristic {
        return characteristicMap[uuid]!!
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
        
        return handlesMap
    }
}
