package org.avmedia.gShockPhoneSync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import kotlinx.coroutines.CoroutineScope

object DeviceManager {

    init {
        startListener()
    }

    private fun startListener() {

        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = ProgressEvents.getPayload("DeviceName")
                if ((deviceName as String) == "") {
                    CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
                        LocalDataStorage.deleteAsync("LastDeviceName")
                    }
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
                    CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
                        LocalDataStorage.deleteAsync("LastDeviceAddress")
                    }
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