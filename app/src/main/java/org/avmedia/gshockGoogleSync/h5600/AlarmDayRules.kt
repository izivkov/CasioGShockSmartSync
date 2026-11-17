package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day-of-week helpers for the phone-side alarm list ([AlarmPool]), plus a reader for the older
 * per-watch-alarm settings (days / vibe only, keyed by alarm time), used once to import them when
 * the alarm list is first created.
 */
object AlarmDayRules {
    val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    /** How long after an alarm rings its sound choice is kept (covers snooze: up to 7 x 5 min). */
    const val SNOOZE_HOLD_MIN = 40L

    private const val PREFS = org.avmedia.gshockGoogleSync.h5600.H5600Prefs.NAME
    private const val KEY_PREFIX = "alarm_dayset_at_"
    private const val KEY_VIBE_PREFIX = "alarm_vibe_only_at_"

    /** Short label, e.g. "Every day", "Mon–Fri", "Sat, Sun", "Mon, Wed, Fri". */
    fun label(days: Set<DayOfWeek>?): String = when {
        days == null || days.isEmpty() || days == ALL_DAYS -> "Every day"
        days == WEEKDAYS -> "Mon–Fri"
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }

    /** Legacy: alarm time (minute of day) -> chosen days. */
    fun load(context: Context): Map<Int, Set<DayOfWeek>> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .mapNotNull { (k, v) ->
                val minute = k.removePrefix(KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val days = (v as? String)?.split(',')?.mapNotNull { s -> s.toIntOrNull()?.let(DayOfWeek::of) }?.toSet()
                if (days.isNullOrEmpty()) null else minute to days
            }.toMap()

    /** Legacy: alarm times (minute of day) marked vibe only. */
    fun loadVibeOnly(context: Context): Set<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filter { (k, v) -> k.startsWith(KEY_VIBE_PREFIX) && v == true }
            .keys.mapNotNull { it.removePrefix(KEY_VIBE_PREFIX).toIntOrNull() }.toSet()
}
