package org.avmedia.gShockSmartSyncCompose.services

import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.api
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.applicationContext
import org.avmedia.gShockSmartSyncCompose.utils.LocalDataStorage
import org.avmedia.gShockSmartSyncCompose.utils.Utils
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
                    LocalDataStorage.delete(applicationContext(), "LastDeviceName")
                } else if (deviceName.contains("CASIO") && LocalDataStorage.get(
                        applicationContext(),
                        "LastDeviceName",
                        ""
                    ) != deviceName
                ) {
                    LocalDataStorage.put(applicationContext(), "LastDeviceName", deviceName)
                }
            },

            EventAction("DeviceAddress") {
                val deviceAddress = ProgressEvents.getPayload("DeviceAddress")
                if ((deviceAddress as String) == "") {
                    LocalDataStorage.delete(applicationContext(), "LastDeviceAddress")
                }
                if (LocalDataStorage.get(
                        applicationContext(),
                        "LastDeviceAddress",
                        ""
                    ) != deviceAddress && api().validateBluetoothAddress(deviceAddress)
                ) {
                    LocalDataStorage.put(
                        applicationContext(),
                        "LastDeviceAddress", deviceAddress
                    )
                }
            },
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }
}