/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:34 p.m.
 */

package org.avmedia.gShockPhoneSync.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import timber.log.Timber
import java.util.UUID

object DeviceCharacteristics {

    lateinit var device: BluetoothDevice

    private val characteristicMap by lazy {
        characteristics.associateBy { it.uuid }.toMap()
    }

    fun init(device: BluetoothDevice) {
        this.device = device
    }

    val characteristics by lazy {
        Connection.servicesOnDevice(device)?.flatMap { service ->
            Timber.i("servicesOnDevice ${service.characteristics}, ${service.includedServices} ...")
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    fun findCharacteristic(uuid: UUID?): BluetoothGattCharacteristic {
        return characteristicMap[uuid]!!
    }

    fun printCharacteristics() {
        println(characteristicMap)
    }
}
