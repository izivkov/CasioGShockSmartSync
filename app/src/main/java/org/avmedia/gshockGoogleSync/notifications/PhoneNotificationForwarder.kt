package org.avmedia.gshockGoogleSync.notifications

/**
 * The decision for each posted phone notification, with one App log line each (the app's name, sent or skipped and
 * why, never the text). A watch that cannot show notifications gets nothing, said once per watch session instead of
 * once per notification. The same skip of the same app within [quietMs] is not repeated (ongoing ones update often).
 */
class PhoneNotificationForwarder(
    private val settings: PhoneNotificationSettings,
    /** True when the connected watch shows app notifications (the library's model check). */
    private val supported: () -> Boolean,
    private val watch: () -> String,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val quietMs: Long = 60_000L,
) {
    private var toldUnsupported: String? = null
    private val lastSkip = HashMap<String, Long>()

    /** True when [n] from [appName] goes to the watch now. */
    @Synchronized
    fun decide(n: PostedNotification, appName: String, ownPackage: String): Boolean {
        val w = watch()
        if (!supported()) {
            if (toldUnsupported != w) {
                toldUnsupported = w
                log("Phone notifications: the connected watch cannot show them, none are sent to it")
            }
            return false
        }
        toldUnsupported = null
        val why = settings.skipReason(w, n, ownPackage)
        if (why == null) {
            log("Phone notification from $appName: sent")
            return true
        }
        val key = "${n.packageName}|$why"
        val now = clock()
        val last = lastSkip[key]
        if (last == null || now - last >= quietMs) {
            lastSkip[key] = now
            log("Phone notification from $appName: skipped ($why)")
        }
        return false
    }

    /** A new watch session: a watch without notifications is told about once more. */
    @Synchronized
    fun newSession() {
        toldUnsupported = null
    }

    companion object {
        /**
         * The connected watch shows phone notifications: its model has them and [serviceFound] (it lists the notification
         * characteristic, which an OCW-S7000 does without showing any).
         */
        fun watchShows(serviceFound: Boolean): Boolean = org.avmedia.gshockapi.WatchInfo.showsAppNotifications(serviceFound)
    }
}

/**
 * Android's notification access for the app. With it off (often lost with the old companion association after a
 * re-pair) no phone notification reaches the app, so the watch list and Settings warn, and the App log says it once.
 */
class NotificationAccessCheck(private val granted: () -> Boolean, private val log: (String) -> Unit) {
    @Volatile private var told = false

    /** The warning to show, or null when access is on or phone notifications are off. */
    fun warning(mode: PhoneNotificationMode): String? {
        if (mode == PhoneNotificationMode.OFF || granted()) {
            told = false
            return null
        }
        if (!told) {
            told = true
            log("Phone notifications: Android's notification access for this app is off, no phone notification reaches the watch")
        }
        return WARNING
    }

    companion object {
        const val WARNING = "Notification access is off: phone notifications cannot reach the watch. Turn it on for this app in Android settings."
    }
}
