package org.avmedia.gShockPhoneSync

import android.bluetooth.BluetoothDevice
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.DeviceCharacteristics
import org.avmedia.gshockapi.io.CachedIO
import timber.log.Timber

object DeviceManager {

    init {
        startListener()
    }

    private fun startListener() {

        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = ProgressEvents.getPayload("DeviceName")
                if ((deviceName as String) == "") {
                    LocalDataStorage.delete("LastDeviceName", applicationContext())
                } else if (deviceName.contains("CASIO") && LocalDataStorage.get(
                        "LastDeviceName",
                        "",
                        applicationContext()
                    ) != deviceName
                ) {
                    LocalDataStorage.put("LastDeviceName", deviceName, applicationContext())
                }
            },

            EventAction("DeviceAddress") {
                val deviceAddress = ProgressEvents.getPayload("DeviceAddress")
                if ((deviceAddress as String) == "") {
                    LocalDataStorage.delete("LastDeviceAddress", applicationContext())
                }
                if (LocalDataStorage.get(
                        "LastDeviceAddress",
                        "",
                        applicationContext()
                    ) != deviceAddress && api().validateBluetoothAddress(deviceAddress)
                ) {
                    LocalDataStorage.put(
                        "LastDeviceAddress", deviceAddress, applicationContext()
                    )
                }
            },
        )

        ProgressEvents.subscriber.runEventActions(this.javaClass.canonicalName, eventActions)
    }
}