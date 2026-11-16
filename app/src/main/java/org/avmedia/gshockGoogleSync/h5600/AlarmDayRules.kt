package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import org.avmedia.gshockapi.model.Alarm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day-of-week rules for watch alarms. The DW-H5600 alarm packet has no day field, so the phone
 * switches a ruled alarm on or off for its *next* ring: an alarm whose next ring falls on a day
 * outside its chosen days is disabled, otherwise enabled. Alarms without a rule (every day) are
 * never touched.
 */
object AlarmDayRules {
    val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private const val PREFS = "h5600_prefs"
    // Keyed by alarm time (minute of day), not slot: the Alarms tab re-sorts alarms by time and
    // writes them back in a new order, so a slot number does not follow an alarm.
    private const val KEY_PREFIX = "alarm_dayset_at_"
    private const val KEY_TIME_PREFIX = "alarm_time_"

    /** Short label, e.g. "Every day", "Mon–Fri", "Sat, Sun", "Mon, Wed, Fri". */
    fun label(days: Set<DayOfWeek>?): String = when {
        days == null || days.isEmpty() || days == ALL_DAYS -> "Every day"
        days == WEEKDAYS -> "Mon–Fri"
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }

    fun minuteOf(alarm: Alarm): Int = alarm.hour * 60 + alarm.minute

    /** Alarm time (minute of day) -> chosen days. Missing = every day (alarm not managed). */
    fun load(context: Context): Map<Int, Set<DayOfWeek>> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .mapNotNull { (k, v) ->
                val minute = k.removePrefix(KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val days = (v as? String)?.split(',')?.mapNotNull { s -> s.toIntOrNull()?.let(DayOfWeek::of) }?.toSet()
                if (days.isNullOrEmpty()) null else minute to days
            }.toMap()

    /** Saves the days for an alarm; an empty set or all seven days removes the rule. */
    fun save(context: Context, alarmMinuteOfDay: Int, days: Set<DayOfWeek>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (days.isEmpty() || days == ALL_DAYS) remove(KEY_PREFIX + alarmMinuteOfDay)
            else putString(KEY_PREFIX + alarmMinuteOfDay, days.sorted().joinToString(",") { it.value.toString() })
        }.apply()
    }

    /** Moves the days + vibe-only settings when an alarm's time is changed in the Alarms tab. */
    fun moveAlarm(context: Context, fromMinute: Int, toMinute: Int) {
        if (fromMinute == toMinute) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val days = p.getString(KEY_PREFIX + fromMinute, null)
        val vibe = p.getBoolean(KEY_VIBE_PREFIX + fromMinute, false)
        p.edit().apply {
            remove(KEY_PREFIX + fromMinute); remove(KEY_VIBE_PREFIX + fromMinute)
            if (days != null) putString(KEY_PREFIX + toMinute, days)
            if (vibe) putBoolean(KEY_VIBE_PREFIX + toMinute, true)
        }.apply()
    }

    /** Alarm times (minute of day) of ruled alarms, as last seen; used to wake up right after them. */
    fun ruledAlarmTimes(context: Context): List<Int> = load(context).keys.toList()

    /** All alarm times (minute of day) as last read from the watch. */
    fun allAlarmTimes(context: Context): List<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith(KEY_TIME_PREFIX) }
            .values.mapNotNull { it as? Int }

    fun rememberTimes(context: Context, alarms: List<Alarm>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            alarms.forEachIndexed { i, a -> putInt(KEY_TIME_PREFIX + i, a.hour * 60 + a.minute) }
        }.apply()
    }

    fun nextRingDate(alarm: Alarm, now: LocalDateTime): LocalDate {
        val alarmMin = alarm.hour * 60 + alarm.minute
        val nowMin = now.hour * 60 + now.minute
        return if (nowMin < alarmMin) now.toLocalDate() else now.toLocalDate().plusDays(1)
    }

    // ---- Per-alarm sound: the watch has one global sound mode, so the phone sets it for the
    // alarm that is about to ring (or is still in its snooze period).

    /** How long after an alarm rings its sound choice is kept (covers snooze: up to 7 x 5 min). */
    const val SNOOZE_HOLD_MIN = 40L
    private const val KEY_VIBE_PREFIX = "alarm_vibe_only_at_"

    fun loadVibeOnly(context: Context): Set<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filter { (k, v) -> k.startsWith(KEY_VIBE_PREFIX) && v == true }
            .keys.mapNotNull { it.removePrefix(KEY_VIBE_PREFIX).toIntOrNull() }.toSet()

    fun saveVibeOnly(context: Context, alarmMinuteOfDay: Int, vibeOnly: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (vibeOnly) putBoolean(KEY_VIBE_PREFIX + alarmMinuteOfDay, true) else remove(KEY_VIBE_PREFIX + alarmMinuteOfDay)
        }.apply()
    }

    /** Ruled alarms ring on their days; unruled alarms ring every day if enabled on the watch. */
    private fun ringsOn(alarm: Alarm, days: Set<DayOfWeek>?, day: DayOfWeek): Boolean =
        if (days.isNullOrEmpty() || days == ALL_DAYS) alarm.enabled else day in days

    private fun ringTimes(alarm: Alarm, days: Set<DayOfWeek>?, now: LocalDateTime) =
        (-8L..8L).map { now.toLocalDate().plusDays(it) }
            .filter { ringsOn(alarm, days, it.dayOfWeek) }
            .map { it.atTime(alarm.hour, alarm.minute) }

    /**
     * Pure: the sound mode the watch should be in now, or null if no alarm is marked vibe-only
     * (then the phone leaves the mode alone). The alarm that counts is the one that rang within
     * the last [SNOOZE_HOLD_MIN] minutes, otherwise the next one to ring.
     */
    fun desiredSoundMode(
        alarms: List<Alarm>,
        rules: Map<Int, Set<DayOfWeek>>,
        vibeOnly: Set<Int>,
        now: LocalDateTime,
    ): org.avmedia.gshockapi.model.SoundMode? {
        if (vibeOnly.isEmpty()) return null
        val recent = alarms.indices.mapNotNull { i ->
            ringTimes(alarms[i], rules[minuteOf(alarms[i])], now)
                .filter { !it.isAfter(now) && it.plusMinutes(SNOOZE_HOLD_MIN).isAfter(now) }
                .maxOrNull()?.let { i to it }
        }.maxByOrNull { it.second }
        val next = alarms.indices.mapNotNull { i ->
            ringTimes(alarms[i], rules[minuteOf(alarms[i])], now).filter { it.isAfter(now) }.minOrNull()?.let { i to it }
        }.minByOrNull { it.second }
        // The snooze hold of a rung alarm never overrides an alarm that rings before the hold ends
        // (e.g. 19:40 sound, 19:44 vibe-only: at 19:42 the watch must already be vibe-only).
        val governing = when {
            recent == null -> next
            next != null && next.second.isBefore(recent.second.plusMinutes(SNOOZE_HOLD_MIN)) -> next
            else -> recent
        }
        val idx = governing?.first ?: return null
        return if (minuteOf(alarms[idx]) in vibeOnly) org.avmedia.gshockapi.model.SoundMode.VIBE_ONLY
        else org.avmedia.gshockapi.model.SoundMode.CUSTOM
    }

    /** Pure: the alarm list with `enabled` set per rule for each alarm's next ring. */
    fun apply(alarms: List<Alarm>, rules: Map<Int, Set<DayOfWeek>>, now: LocalDateTime): List<Alarm> =
        alarms.map { a ->
            val days = rules[minuteOf(a)]
            if (days.isNullOrEmpty() || days == ALL_DAYS) a
            else a.copy(enabled = nextRingDate(a, now).dayOfWeek in days)
        }
}
