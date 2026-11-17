package org.avmedia.gshockGoogleSync.h5600

import android.content.Context

/**
 * The "last applied" line shown on the Alarms and Custom tabs: what the scheduler
 * ([H5600ScheduleRunner]) last changed on the watch. (The old evening/morning sound-mode window
 * that used to live here was replaced by per-alarm settings, see [AlarmPool].)
 */
object H5600Schedule {
    private const val PREFS = org.avmedia.gshockGoogleSync.h5600.H5600Prefs.NAME
    private const val KEY_LAST = "last_applied"

    fun lastApplied(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun recordApplied(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST, text).apply()
    }
}
