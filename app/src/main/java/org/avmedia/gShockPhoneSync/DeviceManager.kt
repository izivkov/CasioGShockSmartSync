package org.avmedia.gShockPhoneSync

import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber


object DeviceManager {

    var name = ""
    var address = ""

    init {
        startListener()
    }

    private fun startListener() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                ProgressEvents["DeviceName"] -> {
                    val deviceName = ProgressEvents["DeviceName"]?.payload
                    if ((deviceName as String) == "") {
                        LocalDataStorage.delete("LastDeviceName", applicationContext())
                    }
                    else if (deviceName.contains("CASIO") && LocalDataStorage.get(
                            "LastDeviceName",
                            "",
                            applicationContext()
                        ) != deviceName)
                     {
                        LocalDataStorage.put("LastDeviceName", deviceName, applicationContext())
                    }
                    name = deviceName
                }

                ProgressEvents["DeviceAddress"] -> {
                    val deviceAddress = ProgressEvents["DeviceAddress"]?.payload
                    if ((deviceAddress as String) == "") {
                        LocalDataStorage.delete("LastDeviceAddress", applicationContext())
                    }
                    if (LocalDataStorage.get(
                            "LastDeviceAddress",
                            "",
                            applicationContext()
                        ) != deviceAddress
                    ) {
                        LocalDataStorage.put(
                            "LastDeviceAddress", deviceAddress as String, applicationContext()
                        )
                    }
                    address = deviceAddress as String
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }

    fun deviceSelected(): Boolean {
        return false
    }
}