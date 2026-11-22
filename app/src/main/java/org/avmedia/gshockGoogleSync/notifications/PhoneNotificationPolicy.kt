package org.avmedia.gshockGoogleSync.notifications

import org.avmedia.gshockGoogleSync.webhook.KeyValueStore

/** What the policy needs to know about one posted phone notification. [importance] is -1000 when Android did not say. */
data class PostedNotification(val packageName: String, val flags: Int, val importance: Int)

/**
 * Which phone notifications go to the watch. Off by default; when on, only apps the person chose, and never
 * ongoing ones (media, navigation, downloads), silent or low-importance ones, group summaries or this app's own.
 * Home Assistant messages do not pass through here: they have their own path and switch.
 */
object PhoneNotificationPolicy {
    // The values of android.app.Notification and NotificationManager, written out so the rule runs in a plain unit test.
    const val FLAG_ONGOING_EVENT = 0x00000002
    const val FLAG_FOREGROUND_SERVICE = 0x00000040
    const val FLAG_GROUP_SUMMARY = 0x00000200
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_UNKNOWN = -1000

    fun allow(enabled: Boolean, allowed: Set<String>, n: PostedNotification, ownPackage: String): Boolean {
        if (n.packageName == ownPackage || n.packageName !in allowed) return false
        // ongoing not dropped
        if (n.flags and FLAG_GROUP_SUMMARY != 0) return false
        return n.importance == IMPORTANCE_UNKNOWN || n.importance >= IMPORTANCE_DEFAULT
    }
}

/** The setting: a switch (off until turned on) and the allowed package names. */
class PhoneNotificationSettings(private val store: KeyValueStore) {
    var enabled: Boolean
        get() = store.get(KEY_ON) == "on"
        set(v) = store.put(KEY_ON, if (v) "on" else "off")

    var allowed: Set<String>
        get() = store.get(KEY_APPS).orEmpty().split('\n').filter { it.isNotBlank() }.toSet()
        set(v) = store.put(KEY_APPS, v.sorted().joinToString("\n"))

    fun setAllowed(packageName: String, on: Boolean) { allowed = if (on) allowed + packageName else allowed - packageName }

    /** True when a notification may go to the watch now. */
    fun allows(n: PostedNotification, ownPackage: String): Boolean = PhoneNotificationPolicy.allow(enabled, allowed, n, ownPackage)

    private companion object {
        const val KEY_ON = "phone_notifications.enabled"
        const val KEY_APPS = "phone_notifications.apps"
    }
}
