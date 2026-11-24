package org.avmedia.gshockGoogleSync.notifications

import android.content.Context
import org.avmedia.gshockGoogleSync.homeassistant.AppStores
import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogSource
import org.avmedia.gshockGoogleSync.services.NotificationMonitorService
import org.avmedia.gshockGoogleSync.watch.CurrentWatch

/** The app's one [NotificationAccessCheck], so the App log says "access is off" once, not on every screen. */
object NotificationAccess {
    @Volatile private var check: NotificationAccessCheck? = null

    private fun check(context: Context): NotificationAccessCheck {
        val app = context.applicationContext
        return check ?: synchronized(this) {
            check ?: NotificationAccessCheck(
                granted = { NotificationMonitorService.isNotificationGranted(app) },
                log = { AppLog.log(LogSource.App, it) },
            ).also { check = it }
        }
    }

    /** The warning for the connected (else the last) watch's setting; null when access is on or the setting is off. */
    fun warning(context: Context, watch: String = CurrentWatch.keyOrLast(context)): String? = when {
        watch.isBlank() -> null
        // A watch that cannot show notifications (the OCW-S7000) needs no access.
        AppStores.haStore(context).notificationsSupported(watch) == false -> null
        else -> check(context).warning(AppStores.phoneNotifications(context).mode(watch))
    }

    fun openSettings(context: Context) = NotificationMonitorService.openNotificationListenerSettings(context)
}
