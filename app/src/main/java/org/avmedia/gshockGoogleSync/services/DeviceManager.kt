package org.avmedia.gshockGoogleSync.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the last connected device's name and address as the API reports them. */
@Singleton
class DeviceManager @Inject constructor(
    private val repository: GShockRepository,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val appContext: Context
) {
    // Serial, off-main scope: storage access may block, and events must be handled in order.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    init {
        // Collected serially on the storage scope, so updates are handled in order.
        scope.launch {
            sessionManager.sessionState
                .map { it.watch }
                .distinctUntilChanged()
                .collect { watch ->
                    persistName(watch.name)
                    persistAddress(watch.address)
                }
        }
    }

    private suspend fun persistName(name: String) {
        if (name.isNotEmpty() && isNewCasioName(name)) {
            LocalDataStorage.put(appContext, "LastDeviceName", name)
        }
    }

    private suspend fun persistAddress(address: String) {
        if (address.isNotEmpty() && isNewValidAddress(address)) {
            LocalDataStorage.put(appContext, "LastDeviceAddress", address)
            LocalDataStorage.addDeviceAddress(appContext, address)
        }
    }

    private fun isNewCasioName(name: String): Boolean =
        name.contains("CASIO") && LocalDataStorage.get(appContext, "LastDeviceName", "") != name

    private fun isNewValidAddress(address: String): Boolean =
        LocalDataStorage.get(appContext, "LastDeviceAddress", "") != address &&
                repository.validateBluetoothAddress(address)
}