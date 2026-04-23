package org.avmedia.gshockGoogleSync.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.services.ReconnectManager
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionDevicePresenceMonitor @Inject constructor(
    private val reconnectManager: ReconnectManager
) {
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
                reconnectManager.onDeviceAppeared(addressValid)
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
