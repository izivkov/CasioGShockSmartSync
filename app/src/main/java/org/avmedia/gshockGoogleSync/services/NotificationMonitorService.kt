package org.avmedia.gshockGoogleSync.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy
import org.avmedia.gshockGoogleSync.notifications.PostedNotification
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.homeassistant.AppStores
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationType
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject

data class NotificationInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val shortText: String,
    val type: NotificationType,
    val timestamp: Long
)

@AndroidEntryPoint
class NotificationMonitorService : NotificationListenerService() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onListenerConnected() {
        super.onListenerConnected()
        isBound = true
        Timber.i("Notification listener service connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isBound = false
        Timber.i("Notification listener service disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Forward notifications only while a watch session is Ready. Read from the Session at the
        // moment of use, so a recreated service instance cannot miss a connection event.
        if (!sessionManager.isReady) return
        // The connected watch's own choice: all apps (upstream's behaviour), only the chosen ones, or off. One App log
        // line per notification (app, sent or skipped and why); a watch without notifications gets nothing.
        val posted = PostedNotification(sbn.packageName, sbn.notification.flags, importanceOf(sbn))
        if (!forwarder(applicationContext).decide(posted, getAppName(sbn.packageName), packageName)) return

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

    private fun importanceOf(sbn: StatusBarNotification): Int = try {
        Ranking().let { r -> if (currentRanking.getRanking(sbn.key, r)) r.importance else PhoneNotificationPolicy.IMPORTANCE_UNKNOWN }
    } catch (t: Throwable) {
        PhoneNotificationPolicy.IMPORTANCE_UNKNOWN
    }

    private fun processNotification(sbn: StatusBarNotification): Result<NotificationInfo> =
        runCatching {
            val notification = sbn.notification
            val extras = notification.extras

            NotificationInfo(
                packageName = sbn.packageName,
                appName = getAppName(sbn.packageName),
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                shortText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
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
        private var isBound = false

        @Volatile private var shared: org.avmedia.gshockGoogleSync.notifications.PhoneNotificationForwarder? = null

        /** One forwarder for the process, so the once-per-session lines stay once. */
        fun forwarder(context: Context): org.avmedia.gshockGoogleSync.notifications.PhoneNotificationForwarder =
            shared ?: synchronized(this) {
                shared ?: org.avmedia.gshockGoogleSync.notifications.PhoneNotificationForwarder(
                    AppStores.phoneNotifications(context.applicationContext),
                    supported = {
                        org.avmedia.gshockGoogleSync.notifications.PhoneNotificationForwarder.watchShows(
                            org.avmedia.gshockapi.ble.Connection.isServiceSupported(org.avmedia.gshockapi.ble.GetSetMode.NOTIFY),
                        )
                    },
                    watch = { org.avmedia.gshockGoogleSync.watch.CurrentWatch.key() },
                    log = { org.avmedia.gshockGoogleSync.log.AppLog.log(org.avmedia.gshockGoogleSync.log.LogSource.App, it) },
                ).also { shared = it }
            }

        /** True when Android lets this app read notifications (Settings, notification access). */
        fun isNotificationGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(packageName) == true
        }


        fun startService(context: Context) {
            if (!isNotificationGranted(context)) {
                openNotificationListenerSettings(context)
            } else {
                if (!isBound) {
                    requestRebind(ComponentName(context, NotificationMonitorService::class.java))
                } else {
                    Timber.d("NotificationMonitorService already bound. Skipping rebind.")
                }
            }
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun createAppNotification(info: NotificationInfo): AppNotification {
            return AppNotification(
                title = info.title,
                text = info.text,
                shortText = info.shortText,
                app = info.appName,
                type = info.type,
                // The time zero padded: "%2d" put a space in the seconds below 10 (a broken frame time).
                timestamp = org.avmedia.gshockapi.NotificationIds.timestamp(
                    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(info.timestamp), java.time.ZoneId.systemDefault())
                )
            )
        }
    }
}
