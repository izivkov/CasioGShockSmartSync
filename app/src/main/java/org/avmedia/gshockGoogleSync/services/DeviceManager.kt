package org.avmedia.gshockGoogleSync.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManager @Inject constructor(
    private val repository: GShockRepository,
    @ApplicationContext private val appContext: Context
) {
    private data class DeviceInfo(
        val name: String,
        val address: String
    )

    init {
        initializeEventListener()
    }

    private fun initializeEventListener() {
        val eventActions = createEventActions()
        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private fun createEventActions(): Array<EventAction> = arrayOf(
        EventAction("DeviceName") { handleDeviceNameEvent() },
        EventAction("DeviceAddress") { handleDeviceAddressEvent() }
    )

    private fun handleDeviceNameEvent() {
        ProgressEvents.getPayload("DeviceName").toString()
            .takeIf { it.isNotEmpty() }
            ?.let { deviceName ->
                when {
                    deviceName.isEmpty() -> LocalDataStorage.delete(appContext, "LastDeviceName")
                    isValidDeviceName(deviceName) -> updateDeviceName(deviceName)
                }
            }
    }

    private fun isValidDeviceName(deviceName: String): Boolean =
        deviceName.contains("CASIO") &&
                LocalDataStorage.get(appContext, "LastDeviceName", "") != deviceName

    private fun updateDeviceName(deviceName: String) {
        LocalDataStorage.put(appContext, "LastDeviceName", deviceName)
    }

    private fun handleDeviceAddressEvent() {
        ProgressEvents.getPayload("DeviceAddress").toString()
            .takeIf { it.isNotEmpty() }
            ?.let { deviceAddress ->
                when {
                    deviceAddress.isEmpty() -> LocalDataStorage.delete(
                        appContext,
                        "LastDeviceAddress"
                    )

                    isValidDeviceAddress(deviceAddress) -> updateDeviceAddress(deviceAddress)
                }
            }
    }

    private fun isValidDeviceAddress(address: String): Boolean =
        LocalDataStorage.get(appContext, "LastDeviceAddress", "") != address &&
                repository.validateBluetoothAddress(address)

    private fun updateDeviceAddress(address: String) {
        LocalDataStorage.put(appContext, "LastDeviceAddress", address)
    }

    fun saveLastConnectedDevice(name: String, address: String) {
        DeviceInfo(name, address).let { deviceInfo ->
            LocalDataStorage.put(appContext, "LastDeviceName", deviceInfo.name)
            LocalDataStorage.put(appContext, "LastDeviceAddress", deviceInfo.address)
        }
    }

    fun clearLastConnectedDevice() {
        listOf("LastDeviceName", "LastDeviceAddress").forEach { key ->
            LocalDataStorage.delete(appContext, key)
        }
    }
}