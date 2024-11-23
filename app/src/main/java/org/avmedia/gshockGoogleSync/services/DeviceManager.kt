package org.avmedia.gshockGoogleSync.services

import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewScoped
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject
import javax.inject.Singleton

@ViewScoped
class DeviceManager @Inject constructor(
    private val repository: GShockRepository,
    @ApplicationContext private val appContext: Context // Inject application context
) {

    init {
        startListener()
    }

    private fun startListener() {
        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = ProgressEvents.getPayload("DeviceName")
                if ((deviceName as String) == "") {
                    LocalDataStorage.delete(appContext, "LastDeviceName")
                } else if (deviceName.contains("CASIO") && LocalDataStorage.get(
                        appContext,
                        "LastDeviceName",
                        ""
                    ) != deviceName
                ) {
                    LocalDataStorage.put(appContext, "LastDeviceName", deviceName)
                }
            },
            EventAction("DeviceAddress") {
                val deviceAddress = ProgressEvents.getPayload("DeviceAddress")
                if ((deviceAddress as String) == "") {
                    LocalDataStorage.delete(appContext, "LastDeviceAddress")
                }
                if (LocalDataStorage.get(
                        appContext,
                        "LastDeviceAddress",
                        ""
                    ) != deviceAddress && repository.validateBluetoothAddress(deviceAddress)
                ) {
                    LocalDataStorage.put(
                        appContext,
                        "LastDeviceAddress", deviceAddress
                    )
                }
            },
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    /**
     * Disconnects the device and clears local data.
     */
    private fun disconnectAndNotify() {
        val deviceName = ProgressEvents.getPayload("Disconnect") as BluetoothDevice
        repository.teardownConnection(deviceName)
    }

    /**
     * Saves the last connected device information for reuse.
     */
    fun saveLastConnectedDevice(name: String, address: String) {
        LocalDataStorage.put(appContext, "LastDeviceName", name)
        LocalDataStorage.put(appContext, "LastDeviceAddress", address)
    }

    /**
     * Clears saved device information when the user explicitly disconnects or forgets the device.
     */
    fun clearLastConnectedDevice() {
        LocalDataStorage.delete(appContext, "LastDeviceName")
        LocalDataStorage.delete(appContext, "LastDeviceAddress")
    }
}
