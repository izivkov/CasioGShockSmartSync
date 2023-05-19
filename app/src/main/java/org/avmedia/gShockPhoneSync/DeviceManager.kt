package org.avmedia.gShockPhoneSync

import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber


object DeviceManager {

    private var deviceSelected = false

    init {
        startListener()
    }

    private fun startListener() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                ProgressEvents["DeviceName"] -> {
                    val deviceName = ProgressEvents["DeviceName"]?.payload
                    if ((deviceName as String).contains("CASIO") && LocalDataStorage.get("LastDeviceName", "", applicationContext()) != deviceName) {
                        LocalDataStorage.put("LastDeviceName", deviceName, applicationContext())
                    }
                }

                ProgressEvents["DeviceAddress"] -> {
                    val deviceAddress = ProgressEvents["DeviceAddress"]?.payload
                    if (LocalDataStorage.get("LastDeviceAddress", "", applicationContext()) != deviceAddress) {
                        LocalDataStorage.put(
                            "LastDeviceAddress", deviceAddress as String, applicationContext()
                        )
                    }
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