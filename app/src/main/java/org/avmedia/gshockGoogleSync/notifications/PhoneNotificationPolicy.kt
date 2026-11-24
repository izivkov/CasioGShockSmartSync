package org.avmedia.gshockGoogleSync.notifications

import org.avmedia.gshockGoogleSync.homeassistant.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchStore

/** What the policy needs to know about one posted phone notification. [importance] is -1000 when Android did not say. */
data class PostedNotification(val packageName: String, val flags: Int, val importance: Int)

/** "Phone notifications on the watch", one choice per watch (owner 2026-11-24). */
enum class PhoneNotificationMode(val label: String) {
    /** Like upstream Smart Sync: every notification while a watch session is ready, the same classification. */
    ALL("All apps"),

    /** Only the chosen apps, and never ongoing, silent, group summary or this app's own notifications. */
    SELECTED("Only selected apps"),
    OFF("Off"),
}

/**
 * Which phone notifications go to the watch. "All apps" passes everything (upstream's behaviour); "Only selected apps"
 * passes the chosen apps and never ongoing ones (media, navigation, downloads), silent or low-importance ones, group
 * summaries or this app's own; "Off" passes nothing. Home Assistant messages do not pass through here.
 */
object PhoneNotificationPolicy {
    // The values of android.app.Notification and NotificationManager, written out so the rule runs in a plain unit test.
    const val FLAG_ONGOING_EVENT = 0x00000002
    const val FLAG_FOREGROUND_SERVICE = 0x00000040
    const val FLAG_GROUP_SUMMARY = 0x00000200
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_UNKNOWN = -1000

    /** Why [n] is not sent, in plain words; null = it is sent. */
    fun skipReason(mode: PhoneNotificationMode, allowed: Set<String>, n: PostedNotification, ownPackage: String): String? = when (mode) {
        PhoneNotificationMode.OFF -> "phone notifications are off"
        PhoneNotificationMode.ALL -> null
        PhoneNotificationMode.SELECTED -> when {
            n.packageName == ownPackage -> "this app's own"
            n.packageName !in allowed -> "app not chosen"
            n.flags and (FLAG_ONGOING_EVENT or FLAG_FOREGROUND_SERVICE) != 0 -> "ongoing"
            n.flags and FLAG_GROUP_SUMMARY != 0 -> "group summary"
            n.importance != IMPORTANCE_UNKNOWN && n.importance < IMPORTANCE_DEFAULT -> "silent"
            else -> null
        }
    }

    fun allow(mode: PhoneNotificationMode, allowed: Set<String>, n: PostedNotification, ownPackage: String): Boolean =
        skipReason(mode, allowed, n, ownPackage) == null
}

/**
 * The setting of each watch ([WatchStore]): the mode and the chosen apps. A watch never set gets "All apps". The one
 * switch of older versions belongs to the legacy owner (the GBX), once: on became "Only selected apps", an explicit
 * off became "Off", never touched stays "All apps".
 */
class PhoneNotificationSettings(private val store: KeyValueStore, private val watches: WatchStore = WatchStore(store)) {
    fun mode(watch: String): PhoneNotificationMode {
        watches.get(watch, W_MODE)?.value?.let { v -> PhoneNotificationMode.entries.firstOrNull { it.name == v }?.let { return it } }
        val old = watches.get(watch, W_MODE, KEY_ON)?.value ?: return PhoneNotificationMode.OFF
        val mode = if (old == "on") PhoneNotificationMode.SELECTED else PhoneNotificationMode.OFF
        watches.put(watch, W_MODE, mode.name)
        return mode
    }

    fun setMode(watch: String, mode: PhoneNotificationMode) = watches.put(watch, W_MODE, mode.name)

    fun allowed(watch: String): Set<String> =
        watches.get(watch, W_APPS, KEY_APPS)?.value.orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun setAllowed(watch: String, apps: Set<String>) = watches.put(watch, W_APPS, apps.sorted().joinToString("\n"))

    fun setAllowed(watch: String, packageName: String, on: Boolean) {
        val now = allowed(watch)
        setAllowed(watch, if (on) now + packageName else now - packageName)
    }

    /** Why [n] is not sent to [watch]; null = it is sent. */
    fun skipReason(watch: String, n: PostedNotification, ownPackage: String): String? =
        PhoneNotificationPolicy.skipReason(mode(watch), allowed(watch), n, ownPackage)

    fun allows(watch: String, n: PostedNotification, ownPackage: String): Boolean = skipReason(watch, n, ownPackage) == null

    private companion object {
        // The one switch of older versions (app-wide).
        const val KEY_ON = "phone_notifications.enabled"
        const val KEY_APPS = "phone_notifications.apps"
        const val W_MODE = "phone_notifications.mode"
        const val W_APPS = "phone_notifications.apps"
    }
}
