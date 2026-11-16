package org.avmedia.gshockGoogleSync.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * App-wide 12/24-hour style. Mirrors the watch's own time format (set / read on the Time tab),
 * so every tab shows times the way the watch does. Persisted; defaults to the phone's setting.
 */
object AppTimeFormat {
    private const val PREFS = "h5600_prefs"
    private const val KEY = "app_time_24h"

    private val _is24h = MutableStateFlow(true)
    val is24h: StateFlow<Boolean> = _is24h.asStateFlow()

    fun init(context: Context) {
        _is24h.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, android.text.format.DateFormat.is24HourFormat(context))
    }

    fun set(context: Context, is24h: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, is24h).apply()
        _is24h.value = is24h
    }

    fun current(): Boolean = _is24h.value

    private fun pattern(seconds: Boolean) =
        if (current()) (if (seconds) "HH:mm:ss" else "HH:mm") else (if (seconds) "h:mm:ss a" else "h:mm a")

    fun format(t: LocalTime, seconds: Boolean = false): String =
        t.format(DateTimeFormatter.ofPattern(pattern(seconds), Locale.getDefault()))

    fun hm(hour: Int, minute: Int): String = format(LocalTime.of(hour, minute))
}
