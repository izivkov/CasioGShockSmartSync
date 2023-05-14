package org.avmedia.gShockPhoneSync

import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

object DeviceManager {
    init {
        startListener()
    }

    private fun startListener() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                ProgressEvents["DeviceName"] -> {
                    val deviceName = ProgressEvents["DeviceName"]?.payload

                    if (LocalDataStorage.getForDevice("name", "", applicationContext()) == "") {
                        LocalDataStorage.putForDevice(
                            "name", deviceName as String, applicationContext()
                        )
                    }

                    val lastModelConnected = LocalDataStorage.get(
                        "LastDeviceConnected",
                        "CASIO GW-B5600",
                        applicationContext()
                    )
                    if (lastModelConnected != null) {
                        if (lastModelConnected.contains("2100") && WatchInfo.model != WatchInfo.WATCH_MODEL.B2100
                        ) {
                            LocalDataStorage.put(
                                "LastDeviceConnected", deviceName as String, applicationContext()
                            )
                        } else if (lastModelConnected.contains("5600") && WatchInfo.model != WatchInfo.WATCH_MODEL.B5600
                        ) {
                            LocalDataStorage.put(
                                "LastDeviceConnected", deviceName as String, applicationContext()
                            )
                        }
                    }
                }

                ProgressEvents["DeviceAddress"] -> {
                    val deviceAddress = ProgressEvents["DeviceAddress"]?.payload

                    if (LocalDataStorage.getForDevice("address", "", applicationContext()) == "") {
                        LocalDataStorage.putForDevice(
                            "address", deviceAddress as String, applicationContext()
                        )
                    }
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }
}