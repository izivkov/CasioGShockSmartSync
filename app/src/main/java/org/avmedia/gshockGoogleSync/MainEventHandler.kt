package org.avmedia.gshockGoogleSync

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.services.NotificationMonitorService
import org.avmedia.gshockGoogleSync.session.Session
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

/**
 * App-level side effects of connection events. Navigation is not handled here: it is derived from
 * the watch Session by [AppScreenState].
 */
class MainEventHandler(
    private val context: GShockApplication,
    private val repository: GShockRepository,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun setupEventSubscription() {
        val eventActions = arrayOf(
            EventAction("WaitForConnection") { handleWaitForConnection() },
            EventAction("HomeTimeUpdated") {},
            EventAction("AppNotification") { handleAppNotification() },
            EventAction("LocationServicesDisabled") { handleLocationServicesDisabled() }
        )

        ProgressEvents.subscribe(eventActions)
        observeSessions()
    }

    private fun observeSessions() {
        scope.launch {
            sessionManager.currentSession.filterNotNull().collect { session ->
                launch { startNotificationServiceWhenReady(session) }
                launch { reportErrorWhenClosed(session) }
            }
        }
    }

    private suspend fun startNotificationServiceWhenReady(session: Session) {
        val state = session.state.first { it.phase !is SessionPhase.Connecting }
        if (state.phase is SessionPhase.Ready && repository.supportsAppNotifications()) {
            NotificationMonitorService.startService(context)
        }
    }

    private suspend fun reportErrorWhenClosed(session: Session) {
        val closed = session.state.first { it.phase is SessionPhase.Closed }.phase
                as SessionPhase.Closed
        if (!closed.isError) return

        val message = closed.reason.ifBlank {
            context.getString(R.string.apierror_ensure_the_official_g_shock_app_is_not_running)
        }
        Timber.e("Session closed with an error: $message")
        AppSnackbar(message)
    }

    private fun handleAppNotification() {
        if (repository.supportsAppNotifications()) {
            val appNotification = ProgressEvents.getPayload("AppNotification") as AppNotification?
            if (appNotification != null) {
                repository.sendAppNotification(appNotification)
            }
        }
    }

    private fun handleWaitForConnection() {
        CoroutineScope(Dispatchers.Default).launch {
            context.companionDeviceCoordinator.checkPairedDevicesOrNotify()
        }
    }

    private fun handleLocationServicesDisabled() {
        CoroutineScope(Dispatchers.Main).launch {
            val activity = ActivityProvider.getCurrentActivity() ?: run {
                Timber.e("No activity available to show location services dialog")
                return@launch
            }

            AlertDialog.Builder(activity)
                .setTitle("Location Services Required")
                .setMessage("Android requires Location Services to be enabled for Bluetooth scanning. This app does not use your location — it is an Android system requirement.")
                .setPositiveButton("Open Settings") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
        }
    }

}