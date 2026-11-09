package org.avmedia.gshockGoogleSync.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    // Serial, off-main scope: storage access may block, and events must be handled in order.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

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
        // Read the payload now; it may be overwritten before the coroutine runs.
        val name = ProgressEvents.getPayload("DeviceName").toString()
        if (name.isEmpty()) return

        scope.launch {
            if (isNewCasioName(name)) {
                LocalDataStorage.put(appContext, "LastDeviceName", name)
            }
        }
    }

    private fun handleDeviceAddressEvent() {
        val address = ProgressEvents.getPayload("DeviceAddress").toString()
        if (address.isEmpty()) return

        scope.launch {
            if (isNewValidAddress(address)) {
                LocalDataStorage.put(appContext, "LastDeviceAddress", address)
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
