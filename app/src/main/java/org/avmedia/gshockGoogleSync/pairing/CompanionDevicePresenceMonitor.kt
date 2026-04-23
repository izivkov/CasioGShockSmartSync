package org.avmedia.gshockGoogleSync.pairing

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val repository: GShockRepository
) {
    private companion object {
        const val DEVICE_APPEAR_DEBOUNCE_MS = 5_000L
    }

    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()
    private var currentConnectJob: Job? = null
    private var currentConnectAddress: String? = null
    private var lastConnectStartedAtMs = 0L

    private val eventActions = arrayOf(
        EventAction("DeviceAppeared") {
            val address = ProgressEvents.getPayload("DeviceAppeared") as? String

            if (address == null) {
                Timber.e("DeviceAppeared triggered but payload address is null or not a String")
                return@EventAction
            }

            val addressValid = address.uppercase()
            Timber.i("Device appeared: $addressValid")

            val shouldStart = synchronized(stateLock) {
                val now = SystemClock.elapsedRealtime()
                when {
                    repository.isConnected() -> false
                    currentConnectJob?.isActive == true -> false
                    currentConnectAddress.equals(addressValid, ignoreCase = true) &&
                        now - lastConnectStartedAtMs < DEVICE_APPEAR_DEBOUNCE_MS -> false
                    else -> {
                        currentConnectAddress = addressValid
                        lastConnectStartedAtMs = now
                        true
                    }
                }
            }

            if (!shouldStart) {
                Timber.i("Skipping duplicate connection attempt for $addressValid")
                return@EventAction
            }

            val job = monitorScope.launch {
                try {
                    Timber.i("$addressValid waitForConnection")
                    repository.waitForConnection(addressValid)
                } catch (e: Exception) {
                    Timber.e(e, "Error occurred while waiting for connection to $addressValid")
                } finally {
                    synchronized(stateLock) {
                        if (currentConnectAddress.equals(addressValid, ignoreCase = true)) {
                            currentConnectJob = null
                        }
                    }
                }
            }

            synchronized(stateLock) {
                currentConnectJob = job
            }
        },
        EventAction("DeviceDisappeared") {
            val address = ProgressEvents.getPayload("DeviceDisappeared") as? String

            if (address == null) {
                Timber.e("DeviceDisappeared triggered but payload address is null")
                return@EventAction
            }

            val addressValid = address.uppercase()
            Timber.i("Device disappeared: $addressValid")

            synchronized(stateLock) {
                if (currentConnectAddress.equals(addressValid, ignoreCase = true)) {
                    currentConnectJob?.cancel()
                    currentConnectJob = null
                }
            }
        }
    )

    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }
}
