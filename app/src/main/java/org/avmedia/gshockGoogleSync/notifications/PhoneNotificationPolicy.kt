package org.avmedia.gshockGoogleSync.notifications

import android.content.Context
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/** What the policy needs to know about one posted notification. [importance] is IMPORTANCE_UNKNOWN when Android did not say. */
data class PostedNotification(val packageName: String, val flags: Int, val importance: Int)

/** "Phone notifications on the watch". */
enum class PhoneNotificationMode {
    /** Every notification while the watch is connected (the default, as before this setting existed). */
    ALL,

    /** Only the chosen apps, and never ongoing, silent, group summary or this app's own notifications. */
    SELECTED,
    OFF,
}

object PhoneNotificationPolicy {
    // The values of android.app.Notification and NotificationManager, so the rule runs in a plain unit test.
    const val FLAG_ONGOING_EVENT = 0x00000002
    const val FLAG_FOREGROUND_SERVICE = 0x00000040
    const val FLAG_GROUP_SUMMARY = 0x00000200
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_UNKNOWN = -1000

    /** Why [n] is not sent to the watch; null when it is sent. */
    fun skipReason(
        mode: PhoneNotificationMode,
        selectedApps: Set<String>,
        n: PostedNotification,
        ownPackage: String
    ): String? = when (mode) {
        PhoneNotificationMode.ALL -> null
        PhoneNotificationMode.OFF -> "phone notifications are off"
        PhoneNotificationMode.SELECTED -> selectedSkipReason(selectedApps, n, ownPackage)
    }

    private fun selectedSkipReason(selectedApps: Set<String>, n: PostedNotification, ownPackage: String): String? =
        when {
            n.packageName == ownPackage -> "own notification"
            n.packageName !in selectedApps -> "app not selected"
            n.flags and (FLAG_ONGOING_EVENT or FLAG_FOREGROUND_SERVICE) != 0 -> "ongoing"
            n.flags and FLAG_GROUP_SUMMARY != 0 -> "group summary"
            n.importance != IMPORTANCE_UNKNOWN && n.importance < IMPORTANCE_DEFAULT -> "silent"
            else -> null
        }
}

/** The stored choice and the selected apps. Nothing stored means "All apps". */
class PhoneNotificationSettings(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    var mode: PhoneNotificationMode
        get() = PhoneNotificationMode.entries.firstOrNull { it.name == read(KEY_MODE) } ?: PhoneNotificationMode.ALL
        set(value) = write(KEY_MODE, value.name)

    var selectedApps: Set<String>
        get() = read(KEY_APPS).orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        set(value) = write(KEY_APPS, value.sorted().joinToString(","))

    fun skipReason(n: PostedNotification, ownPackage: String): String? =
        PhoneNotificationPolicy.skipReason(mode, selectedApps, n, ownPackage)

    companion object {
        const val KEY_MODE = "phoneNotificationsMode"
        const val KEY_APPS = "phoneNotificationsApps"

        fun from(context: Context): PhoneNotificationSettings {
            val appContext = context.applicationContext
            return PhoneNotificationSettings(
                read = { LocalDataStorage.get(appContext, it) },
                write = { key, value -> LocalDataStorage.put(appContext, key, value) },
            )
        }
    }
}
