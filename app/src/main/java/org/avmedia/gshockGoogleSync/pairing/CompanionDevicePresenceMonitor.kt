package org.avmedia.gshockGoogleSync.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionDevicePresenceMonitor @Inject constructor(
    // 1. Inject the repository directly in the constructor
    private val repository: GShockRepository
) {
    // Use a class-level scope to manage coroutines safely
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val eventActions = arrayOf(
        EventAction("DeviceAppeared") {
            val address = ProgressEvents.getPayload("DeviceAppeared") as? String

            if (address == null) {
                Timber.e("DeviceAppeared triggered but payload address is null or not a String")
                return@EventAction
            }

            val addressValid = address.uppercase()
            Timber.i("Device appeared: $addressValid")

            monitorScope.launch {
                try {
                    // Now 'repository' is guaranteed to be initialized
                    if (!repository.isConnected()) {
                        Timber.i("$addressValid waitForConnection")
                        repository.waitForConnection(addressValid)
                    } else {
                        Timber.i("Device already connected. Skipping wait.")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error occurred while waiting for connection to $addressValid")
                }
            }
        },
        EventAction("DeviceDisappeared") {
            val address = ProgressEvents.getPayload("DeviceDisappeared") as? String

            if (address == null) {
                Timber.e("DeviceDisappeared triggered but payload address is null")
                return@EventAction
            }

            Timber.i("Device disappeared: $address")
        }
    )

    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }
}
