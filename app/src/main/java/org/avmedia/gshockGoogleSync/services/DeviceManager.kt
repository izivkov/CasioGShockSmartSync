package org.avmedia.gshockGoogleSync.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the last connected device's name and address as the API reports them. */
@Singleton
class DeviceManager @Inject constructor(
    private val repository: GShockRepository,
    @param:ApplicationContext private val appContext: Context
) {
    init {
        ProgressEvents.runEventActions(
            Utils.AppHashCode(),
            arrayOf(
                EventAction("DeviceName") { handleDeviceNameEvent() },
                EventAction("DeviceAddress") { handleDeviceAddressEvent() }
            )
        )
    }

    private fun handleDeviceNameEvent() {
        ProgressEvents.getPayload("DeviceName").toString()
            .takeIf { it.isNotEmpty() && isNewCasioName(it) }
            ?.let { LocalDataStorage.put(appContext, "LastDeviceName", it) }
    }

    private fun handleDeviceAddressEvent() {
        ProgressEvents.getPayload("DeviceAddress").toString()
            .takeIf { it.isNotEmpty() && isNewValidAddress(it) }
            ?.let { address ->
                LocalDataStorage.put(appContext, "LastDeviceAddress", address)
                CoroutineScope(Dispatchers.IO).launch {
                    LocalDataStorage.addDeviceAddress(appContext, address)
                }
            }
    }

    private fun isNewCasioName(name: String): Boolean =
        name.contains("CASIO") && LocalDataStorage.get(appContext, "LastDeviceName", "") != name

    private fun isNewValidAddress(address: String): Boolean =
        LocalDataStorage.get(appContext, "LastDeviceAddress", "") != address &&
                repository.validateBluetoothAddress(address)
}
