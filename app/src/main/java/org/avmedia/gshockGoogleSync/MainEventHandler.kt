package org.avmedia.gshockGoogleSync

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.services.NotificationMonitorService
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockGoogleSync.utils.Utils
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainEventHandler(
    private val context: GShockApplication,
    private val repository: GShockRepository,
    private val screenManager: IScreenManager
) {
    fun setupEventSubscription() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {},
            EventAction("WatchInitializationCompleted") { handleWatchInitialization() },
            EventAction("ConnectionFailed") { handleConnectionFailure() },
            EventAction("Error") { handleError() },
            EventAction("WaitForConnection") { handleWaitForConnection() },
            EventAction("Disconnect") { handleDisconnect() },
            EventAction("HomeTimeUpdated") {},
            EventAction("RunActions") { handleRunAction() },
            EventAction("AppNotification") { handleAppNotification() }
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private fun handleWatchInitialization() {
        if (repository.supportsAppNotifications()) {
            NotificationMonitorService.startService(context)
        }
        screenManager.showContentSelector(repository)
    }

    private fun handleAppNotification() {
        if (repository.supportsAppNotifications()) {
            val appNotification = ProgressEvents.getPayload("AppNotification") as AppNotification?
            if (appNotification != null) {
                repository.sendAppNotification(appNotification)
            }
        }
    }

    private fun handleConnectionFailure() {
        Timber.e("Failed to connect to the watch")
    }

    private fun handleRunAction() {
        screenManager.showRunActionsScreen()

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            screenManager.showContentSelector(repository)
        }
    }

    private fun handleError() {
        val message = ProgressEvents.getPayload("Error") as String?
            ?: context.getString(
                R.string.apierror_ensure_the_official_g_shock_app_is_not_running
            )

        repository.disconnect()
        screenManager.showError(message)
        screenManager.showPreConnectionScreen()
    }

    private fun handleDisconnect() {
        ProgressEvents.getPayload("Disconnect")?.let { device ->
            repository.teardownConnection(device as BluetoothDevice)
        }

        Executors.newSingleThreadScheduledExecutor().schedule({
            screenManager.showInitialScreen()
        }, 0L, TimeUnit.SECONDS)
    }

    private fun handleWaitForConnection() {
        CoroutineScope(Dispatchers.Default).launch {
            repository.waitForConnection()
        }
    }
}
