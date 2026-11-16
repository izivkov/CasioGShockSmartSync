package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import org.avmedia.gshockapi.model.SoundMode
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Weekday "vibe only" window for the DW-H5600 sound mode.
 *
 * Rule: VIBE_ONLY from [Config.startMin] until [Config.endMin] when the morning that the window
 * ends on is Monday-Friday; CUSTOM at all other times. With start > end the window runs
 * overnight (e.g. 22:00 -> 08:00 means Sunday 22:00 to Monday 08:00 is vibe-only, Friday 22:00
 * to Saturday 08:00 is not). With start <= end it is a same-day window on weekdays.
 *
 * The phone can only apply this when the watch connects (auto time sync or a button press),
 * so the mode changes at the first connection inside / after the window, not at the exact time.
 */
object H5600Schedule {
    data class Config(
        val enabled: Boolean = false,
        val startMin: Int = 20 * 60 + 30,
        val endMin: Int = 9 * 60,
    )

    /** Next moment (strictly after [now]) at which the desired mode can change: start or end time. */
    fun nextBoundary(cfg: Config, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        return listOf(cfg.startMin, cfg.endMin)
            .flatMap { m -> listOf(0L, 1L).map { d -> today.plusDays(d).atTime(m / 60, m % 60) } }
            .filter { it.isAfter(now) }
            .min()
    }

    private const val PREFS = "h5600_prefs"
    private const val KEY_ENABLED = "schedule_enabled"
    private const val KEY_START = "schedule_start_min"
    private const val KEY_END = "schedule_end_min"
    private const val KEY_LAST = "last_applied"

    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun desiredMode(cfg: Config, now: LocalDateTime): SoundMode {
        val t = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        val morning = if (cfg.startMin > cfg.endMin) {
            when {
                t >= cfg.startMin -> today.plusDays(1)
                t < cfg.endMin -> today
                else -> null
            }
        } else {
            if (t >= cfg.startMin && t < cfg.endMin) today else null
        }
        return if (morning != null && morning.dayOfWeek in WEEKDAYS) SoundMode.VIBE_ONLY else SoundMode.CUSTOM
    }

    fun load(context: Context): Config =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { p ->
            val d = Config()
            Config(
                enabled = p.getBoolean(KEY_ENABLED, d.enabled),
                startMin = p.getInt(KEY_START, d.startMin),
                endMin = p.getInt(KEY_END, d.endMin),
            )
        }

    fun save(context: Context, cfg: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, cfg.enabled)
            .putInt(KEY_START, cfg.startMin)
            .putInt(KEY_END, cfg.endMin)
            .apply()
    }

    fun lastApplied(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun recordApplied(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST, text).apply()
    }

    fun formatMin(min: Int): String = org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(min / 60, min % 60)
}
