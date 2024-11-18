package org.avmedia.gShockPhoneSync.services

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DeviceManager {

    private const val INACTIVITY_TIMEOUT_MINUTES = 5L
    private var inactivityHandler: Handler? = null
    private var inactivityRunnable: Runnable? = null

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
     * Starts the inactivity watcher that triggers disconnect if the user is idle.
     */
    fun startInactivityWatcher(application: Application) {
        inactivityHandler = Handler(Looper.getMainLooper())
        inactivityRunnable = Runnable {
            disconnectAndNotify(application)
        }
        resetInactivityTimer()
    }

    /**
     * Resets the inactivity timer to avoid disconnecting due to user activity.
     */
    private fun resetInactivityTimer() {
        inactivityHandler?.removeCallbacks(inactivityRunnable!!)
        inactivityHandler?.postDelayed(
            inactivityRunnable!!,
            TimeUnit.MINUTES.toMillis(INACTIVITY_TIMEOUT_MINUTES)
        )
    }

    /**
     * Cancels the inactivity watcher entirely.
     */
    fun stopInactivityWatcher() {
        inactivityHandler?.removeCallbacks(inactivityRunnable!!)
        inactivityHandler = null
        inactivityRunnable = null
    }

    /**
     * Disconnects the device and clears local data.
     */
    private fun disconnectAndNotify(application: Application) {
        stopInactivityWatcher()
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
