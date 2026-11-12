package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import org.avmedia.gshockapi.EventAction
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single process-scoped bridge that registers ONCE at app launch to observe low-level connection
 * events (from [EventSource], normally the library's ProgressEvents) and translate them into
 * high-level [SessionManager] actions.
 */
@Singleton
class SessionEventBridge @Inject constructor(
    private val sessionManager: SessionManager
) {
    private var subscription: AutoCloseable? = null

    fun setupEventBridge(events: EventSource = ProgressEventsSource) {
        if (subscription != null) return
        Timber.i("Setting up SessionEventBridge")
        val eventActions = arrayOf(
            EventAction("ConnectionStarted") {
                sessionManager.onConnecting()
            },
            EventAction("DeviceName") {
                (events.payload("DeviceName") as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(sessionManager::onDeviceName)
            },
            EventAction("DeviceAddress") {
                (events.payload("DeviceAddress") as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(sessionManager::onDeviceAddress)
            },
            EventAction("ConnectionSetupComplete") {
                val device = events.payload("ConnectionSetupComplete") as? BluetoothDevice
                sessionManager.onSetupComplete(device)
            },
            EventAction("WatchInitializationCompleted") {
                sessionManager.onConnected()
            },
            EventAction("Disconnect") {
                val device = events.payload("Disconnect") as? BluetoothDevice
                sessionManager.onDisconnected(device, "Watch Disconnected")
            },
            EventAction("ConnectionFailed") {
                sessionManager.onDisconnected(reason = "Connection Failed")
            },
            EventAction("ApiError") {
                val errorMsg = events.payload("ApiError")?.toString() ?: "API Error"
                sessionManager.onApiError(errorMsg)
            },
            EventAction("RunActions") {
                sessionManager.onRunActionsRequested()
            },
            EventAction("Error") {
                // A blank reason means "use the default message"; the UI layer owns that text.
                val errorMsg = events.payload("Error") as? String ?: ""
                sessionManager.onError(errorMsg)
            }
        )

        subscription = events.subscribe(eventActions)
    }
}