package org.avmedia.gshockGoogleSync.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
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
            val address = (ProgressEvents.getPayload("DeviceAppeared") as String)
            val addressValid = address.uppercase()

            monitorScope.launch {
                // Now 'repository' is guaranteed to be initialized
                if (!repository.isConnected()) {
                    repository.waitForConnection(addressValid)
                }
            }
        },
        EventAction("DeviceDisappeared") {
            val address = ProgressEvents.getPayload("DeviceDisappeared") as String
            // Handle disappearance if needed
        }
    )

    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }
}
