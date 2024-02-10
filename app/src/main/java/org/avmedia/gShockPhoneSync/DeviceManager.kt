package org.avmedia.gShockPhoneSync

import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

object DeviceManager {

    init {
        startListener()
    }

    private fun startListener() {

        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = ProgressEvents.getPayload("DeviceName")
                if ((deviceName as String) == "") {
                    LocalDataStorage.delete("LastDeviceName")
                } else if (deviceName.contains("CASIO") && LocalDataStorage.get(
                        "LastDeviceName",
                        ""
                    ) != deviceName
                ) {
                    LocalDataStorage.put("LastDeviceName", deviceName)
                }
            },

            EventAction("DeviceAddress") {
                val deviceAddress = ProgressEvents.getPayload("DeviceAddress")
                if ((deviceAddress as String) == "") {
                    LocalDataStorage.delete("LastDeviceAddress")
                }
                if (LocalDataStorage.get(
                        "LastDeviceAddress",
                        ""
                    ) != deviceAddress && api().validateBluetoothAddress(deviceAddress)
                ) {
                    LocalDataStorage.put(
                        "LastDeviceAddress", deviceAddress
                    )
                }
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }
}