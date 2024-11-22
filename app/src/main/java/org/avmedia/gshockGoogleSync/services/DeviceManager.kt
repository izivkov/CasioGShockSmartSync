package org.avmedia.gshockGoogleSync.services

import android.app.Application
import android.bluetooth.BluetoothDevice
import org.avmedia.gshockGoogleSync.MainActivity.Companion.applicationContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

object DeviceManager {

    private lateinit var repository: GShockRepository

    init {
        startListener()
    }

    /**
     * Initializes the DeviceManager with the given GShockAPI instance.
     * Uses WeakReference to prevent memory leaks.
     */
    fun initialize(repository: GShockRepository) {
        this.repository = repository
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
                    ) != deviceAddress && repository.validateBluetoothAddress(deviceAddress)
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

    /**
     * Disconnects the device and clears local data.
     */
    private fun disconnectAndNotify(application: Application) {
        val deviceName = ProgressEvents.getPayload("Disconnect") as BluetoothDevice

        repository.teardownConnection(deviceName)

        // Optionally, notify the user or update the UI
        // You can send a local broadcast or use an event bus to notify.
    }

    /**
     * Saves the last connected device information for reuse.
     */
    fun saveLastConnectedDevice(application: Application, name: String, address: String) {
        LocalDataStorage.put(application, "LastDeviceName", name)
        LocalDataStorage.put(application, "LastDeviceAddress", address)
    }

    /**
     * Clears saved device information when the user explicitly disconnects or forgets the device.
     */
    fun clearLastConnectedDevice(application: Application) {
        LocalDataStorage.delete(application, "LastDeviceName")
        LocalDataStorage.delete(application, "LastDeviceAddress")
    }
}
