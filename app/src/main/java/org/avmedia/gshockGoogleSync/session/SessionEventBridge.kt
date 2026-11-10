package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single process-scoped bridge that registers ONCE at app launch to observe low-level [ProgressEvents]
 * and translate connection events into high-level [SessionManager] actions.
 */
@Singleton
class SessionEventBridge @Inject constructor(
    private val sessionManager: SessionManager
) {
    fun setupEventBridge() {
        Timber.i("Setting up SessionEventBridge")
        val eventActions = arrayOf(
            EventAction("WatchInitializationCompleted") {
                sessionManager.onConnected()
            },
            EventAction("Disconnect") {
                val device = ProgressEvents.getPayload("Disconnect") as? BluetoothDevice
                sessionManager.onDisconnected(device, "Watch Disconnected")
            },
            EventAction("ConnectionFailed") {
                sessionManager.onDisconnected(reason = "Connection Failed")
            },
            EventAction("Error") {
                val errorMsg = ProgressEvents.getPayload("Error") as? String ?: "Connection Error"
                sessionManager.onDisconnected(reason = errorMsg)
            }
        )

        ProgressEvents.runEventActions(Utils.AppHashCode() + "_SessionBridge", eventActions)
    }
}
