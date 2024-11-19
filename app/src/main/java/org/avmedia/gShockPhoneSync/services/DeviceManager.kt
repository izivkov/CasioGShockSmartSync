package org.avmedia.gShockPhoneSync.services

import android.app.Application
import android.bluetooth.BluetoothDevice
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import java.lang.ref.WeakReference

object DeviceManager {

    private var apiReference: WeakReference<GShockAPI>? = null

    init {
        startListener()
    }

    /**
     * Initializes the DeviceManager with the given GShockAPI instance.
     * Uses WeakReference to prevent memory leaks.
     */
    fun initialize(api: GShockAPI) {
        apiReference = WeakReference(api)
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

    /**
     * Disconnects the device and clears local data.
     */
    private fun disconnectAndNotify(application: Application) {
        val api = apiReference?.get() ?: return // Exit if API reference is null
        val deviceName = ProgressEvents.getPayload("Disconnect") as BluetoothDevice

        api.teardownConnection(deviceName)

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
