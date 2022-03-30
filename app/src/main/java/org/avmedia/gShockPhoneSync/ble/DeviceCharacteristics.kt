/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:34 p.m.
 */

package org.avmedia.gShockPhoneSync.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import org.avmedia.gShockPhoneSync.BleTestActivity
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import timber.log.Timber
import java.util.UUID

object DeviceCharacteristics {

    lateinit var device:BluetoothDevice

    private val characteristicMap by lazy {
        characteristics.associateBy { it.uuid }.toMap()
    }

    fun init (device: BluetoothDevice) {
        this.device = device
    }

    val characteristics by lazy {
        Connection.servicesOnDevice(device)?.flatMap { service ->
            Timber.i("servicesOnDevice $service ...")
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    val characteristicProperties by lazy {
        characteristics.associateWith { characteristic ->
            mutableListOf<BleTestActivity.CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(BleTestActivity.CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(BleTestActivity.CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(BleTestActivity.CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(BleTestActivity.CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(BleTestActivity.CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }
    }

    fun findCharacteristic(uuid: UUID?) :BluetoothGattCharacteristic {
        return characteristicMap[uuid]!!
    }

    fun printCharacteristics() {
        println(characteristicMap)
    }
}
