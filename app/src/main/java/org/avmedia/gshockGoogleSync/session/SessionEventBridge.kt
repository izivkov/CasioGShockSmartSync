package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
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
    private var subscription: AutoCloseable? = null

    fun setupEventBridge() {
        if (subscription != null) return
        Timber.i("Setting up SessionEventBridge")
        val eventActions = arrayOf(
            EventAction("ConnectionStarted") {
                sessionManager.onConnecting()
            },
            EventAction("DeviceName") {
                (ProgressEvents.getPayload("DeviceName") as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(sessionManager::onDeviceName)
            },
            EventAction("DeviceAddress") {
                (ProgressEvents.getPayload("DeviceAddress") as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(sessionManager::onDeviceAddress)
            },
            EventAction("ConnectionSetupComplete") {
                val device = ProgressEvents.getPayload("ConnectionSetupComplete") as? BluetoothDevice
                sessionManager.onSetupComplete(device)
            },
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
            EventAction("ApiError") {
                val errorMsg = ProgressEvents.getPayload("ApiError")?.toString() ?: "API Error"
                sessionManager.onApiError(errorMsg)
            },
            EventAction("RunActions") {
                sessionManager.onRunActionsRequested()
            },
            EventAction("Error") {
                // A blank reason means "use the default message"; the UI layer owns that text.
                val errorMsg = ProgressEvents.getPayload("Error") as? String ?: ""
                sessionManager.onError(errorMsg)
            }
        )

        subscription = ProgressEvents.subscribe(eventActions)
    }
}