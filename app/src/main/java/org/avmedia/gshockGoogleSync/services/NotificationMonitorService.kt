package org.avmedia.gshockGoogleSync.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.NotificationType
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

data class NotificationInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val shortText: String,
    val type: NotificationType,
    val timestamp: Long
)

class NotificationMonitorService : NotificationListenerService() {
    private data class ServiceState(
        val isRunning: Boolean = false,
        val eventActions: List<EventAction> = emptyList()
    )

    private val _state = MutableStateFlow(ServiceState())
    private val state: StateFlow<ServiceState> = _state.asStateFlow()

    init {
        initializeEventActions()
    }

    private fun initializeEventActions() {
        val actions = listOf(
            EventAction("WatchInitializationCompleted") { updateRunningState(true) },
            EventAction("Disconnect") { updateRunningState(false) }
        )
        _state.value = _state.value.copy(eventActions = actions)
        subscribeToEvents()
    }

    private fun subscribeToEvents() {
        ProgressEvents.runEventActions(Utils.AppHashCode(), state.value.eventActions.toTypedArray())
    }

    private fun updateRunningState(running: Boolean) {
        _state.value = _state.value.copy(isRunning = running)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.i("Notification listener service connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!state.value.isRunning) return

        processNotification(sbn)
            .onSuccess { notificationInfo ->
                Timber.i("Notification received: $notificationInfo")
                createAppNotification(notificationInfo)
                    .let { appNotification ->
                        ProgressEvents.onNext("AppNotification", appNotification)
                    }
            }
            .onFailure { error ->
                Timber.e(error, "Error processing notification")
            }
    }

    private fun processNotification(sbn: StatusBarNotification): Result<NotificationInfo> =
        runCatching {
            val notification = sbn.notification
            val extras = notification.extras

            NotificationInfo(
                packageName = sbn.packageName,
                appName = getAppName(sbn.packageName),
                title = extras.getString(Notification.EXTRA_TITLE).orEmpty(),
                text = extras.getString(Notification.EXTRA_TEXT).orEmpty(),
                shortText = extras.getString(Notification.EXTRA_SUB_TEXT).orEmpty(),
                type = classifyNotificationType(sbn.packageName, notification),
                timestamp = sbn.postTime
            )
        }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private val knownAppCategories = mapOf(
        "com.google.android.calendar" to NotificationType.CALENDAR,
        "com.google.android.gm" to NotificationType.EMAIL,
        "com.whatsapp" to NotificationType.MESSAGE,
        "com.google.android.apps.messaging" to NotificationType.MESSAGE,
        "com.facebook.messenger" to NotificationType.MESSAGE,
        "com.facebook.katana" to NotificationType.GENERIC,      // Social
        "com.twitter.android" to NotificationType.GENERIC,      // Social
        "com.instagram.android" to NotificationType.GENERIC,    // Social
        "com.linkedin.android" to NotificationType.GENERIC      // Social
    )

    private fun classifyNotificationType(
        packageName: String,
        notification: Notification
    ): NotificationType {
        // First check known apps
        knownAppCategories[packageName]?.let { return it }

        // Then check notification category
        return when (notification.category) {
            Notification.CATEGORY_EMAIL -> NotificationType.EMAIL
            Notification.CATEGORY_MESSAGE -> NotificationType.MESSAGE
            Notification.CATEGORY_EVENT -> NotificationType.CALENDAR
            Notification.CATEGORY_SOCIAL -> NotificationType.GENERIC
            Notification.CATEGORY_CALL -> NotificationType.PHONE_CALL
            else -> NotificationType.GENERIC
        }
    }

    companion object {
        private var isRunning = false

        private fun isNotificationGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(packageName) == true
        }

        private fun toggleNotificationListenerService(context: Context) {
            val pm = context.packageManager
            val componentName = ComponentName(context, NotificationMonitorService::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun startService(context: Context) {
            if (!isNotificationGranted(context)) {
                openNotificationListenerSettings(context)
            } else {
                // Force reconnect the service...
                // val toggleIntent = Intent(context, NotificationMonitorService::class.java)
                // context.startService(toggleIntent)

                // Toggling is more reliable than restarting the service
                toggleNotificationListenerService(context)
            }
        }

        private fun openNotificationListenerSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun createAppNotification(info: NotificationInfo): AppNotification {
            fun Long.toFormattedDateTime(): String {
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = this@toFormattedDateTime
                }

                return String.format(
                    java.util.Locale.US,
                    "%04d%02d%02dT%02d%02d%2d",
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH) + 1,
                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                    calendar.get(java.util.Calendar.SECOND)
                )
            }

            return AppNotification(
                title = info.title,
                text = info.text,
                shortText = info.shortText,
                app = info.appName,
                type = info.type,
                timestamp = info.timestamp.toFormattedDateTime()
            )
        }
    }
}