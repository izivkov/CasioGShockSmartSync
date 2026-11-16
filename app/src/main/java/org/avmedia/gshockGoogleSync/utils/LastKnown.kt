package org.avmedia.gshockGoogleSync.utils

import android.content.Context

/**
 * Last values read from the watch, kept on the phone so screens can show them right after an app
 * restart (the library cache is in memory only). Absent = never read -> screens show a spinner.
 */
object LastKnown {
    private const val PREFS = "last_known_watch_values"
    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun int(context: Context, key: String): Int? = p(context).takeIf { it.contains(key) }?.getInt(key, 0)
    fun string(context: Context, key: String): String? = p(context).getString(key, null)
    fun bool(context: Context, key: String): Boolean? = p(context).takeIf { it.contains(key) }?.getBoolean(key, false)

    fun put(context: Context, key: String, v: Int) = p(context).edit().putInt(key, v).apply()
    fun put(context: Context, key: String, v: String) = p(context).edit().putString(key, v).apply()
    fun put(context: Context, key: String, v: Boolean) = p(context).edit().putBoolean(key, v).apply()
}
