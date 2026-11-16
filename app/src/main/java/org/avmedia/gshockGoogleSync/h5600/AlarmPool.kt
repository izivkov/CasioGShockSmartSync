package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.SoundMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.UUID

/**
 * Phone-side alarm list for the DW-H5600: any number of alarms, each with its own days and
 * vibe-only / sound choice. The watch only has [SLOTS] usable alarm slots, so the phone keeps the
 * alarms that ring next in those slots and reloads them as alarms ring ([plan]).
 *
 * Watch slots ring every day, so an alarm is only loaded when its next ring is less than 24 h away
 * (otherwise it would also ring today at the same time).
 */
object AlarmPool {
    const val SLOTS = 4
    private const val HOLD_MIN = AlarmDayRules.SNOOZE_HOLD_MIN

    data class PhoneAlarm(
        val id: String = UUID.randomUUID().toString(),
        val hour: Int,
        val minute: Int,
        val enabled: Boolean = true,
        val days: Set<DayOfWeek> = AlarmDayRules.ALL_DAYS,
        val vibeOnly: Boolean = false,
        val label: String = "", // phone-side only; the watch has no alarm names
        val snooze: Boolean = true,
    ) {
        val minuteOfDay: Int get() = hour * 60 + minute
    }

    // ---- storage ----
    private const val PREFS = "h5600_prefs"
    private const val KEY_POOL = "alarm_pool_json"
    private const val KEY_LOADED = "alarm_pool_loaded_ids"

    fun load(context: Context): List<PhoneAlarm>? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_POOL, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PhoneAlarm(
                        id = o.getString("id"),
                        hour = o.getInt("hour"),
                        minute = o.getInt("minute"),
                        enabled = o.optBoolean("enabled", true),
                        days = o.optString("days", "").split(',').mapNotNull { it.toIntOrNull()?.let(DayOfWeek::of) }
                            .toSet().ifEmpty { AlarmDayRules.ALL_DAYS },
                        vibeOnly = o.optBoolean("vibeOnly", false),
                        label = o.optString("label", ""),
                        snooze = o.optBoolean("snooze", true),
                    )
                }
            }.getOrNull()
        }

    fun save(context: Context, alarms: List<PhoneAlarm>) {
        val arr = JSONArray()
        alarms.sortedBy { it.minuteOfDay }.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id).put("hour", a.hour).put("minute", a.minute).put("enabled", a.enabled)
                    .put("days", a.days.sorted().joinToString(",") { it.value.toString() })
                    .put("vibeOnly", a.vibeOnly)
                    .put("label", a.label)
                    .put("snooze", a.snooze),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_POOL, arr.toString()).apply()
    }

    fun loadedIds(context: Context): List<String?> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOADED, null)
            ?.split(',')?.map { it.ifEmpty { null } } ?: List(SLOTS) { null }

    fun saveLoadedIds(context: Context, ids: List<String?>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LOADED, ids.joinToString(",") { it ?: "" }).apply()
    }

    /** First use: take over the watch's current alarms, with any per-alarm settings made earlier. */
    fun importFrom(watch: List<Alarm>, rules: Map<Int, Set<DayOfWeek>>, vibe: Set<Int>): List<PhoneAlarm> =
        watch.take(SLOTS).map { a ->
            val m = a.hour * 60 + a.minute
            PhoneAlarm(hour = a.hour, minute = a.minute, enabled = a.enabled,
                days = rules[m] ?: AlarmDayRules.ALL_DAYS, vibeOnly = m in vibe)
        }

    // ---- pure planning ----

    /** Ring times of an alarm from 8 days back to 8 days ahead. */
    private fun rings(a: PhoneAlarm, now: LocalDateTime): List<LocalDateTime> =
        if (!a.enabled) emptyList()
        else (-8L..8L).map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in a.days }
            .map { it.atTime(a.hour, a.minute) }

    fun nextRing(a: PhoneAlarm, now: LocalDateTime): LocalDateTime? = rings(a, now).firstOrNull { it.isAfter(now) }
    private fun lastRing(a: PhoneAlarm, now: LocalDateTime): LocalDateTime? = rings(a, now).lastOrNull { !it.isAfter(now) }

    /**
     * Which alarm goes in which watch slot. Keeps alarms in the slot they already occupy (so a
     * ringing / snoozing slot is not rewritten), keeps alarms that rang in the last [HOLD_MIN]
     * minutes (snooze), then fills free slots with the alarms due soonest within 24 h.
     */
    fun plan(pool: List<PhoneAlarm>, current: List<String?>, now: LocalDateTime): List<String?> {
        val byId = pool.associateBy { it.id }
        val dueSoon = pool.mapNotNull { a -> nextRing(a, now)?.let { a to it } }
            .filter { (_, t) -> t.isBefore(now.plusHours(24)) }
            .sortedBy { it.second }.map { it.first.id }
        val snoozing = pool.filter { a ->
            lastRing(a, now)?.let { it.plusMinutes(HOLD_MIN).isAfter(now) } == true
        }.map { it.id }
        val wanted = (snoozing + dueSoon).distinct().take(SLOTS)

        val slots = MutableList<String?>(SLOTS) { i -> current.getOrNull(i)?.takeIf { it in wanted && it in byId } }
        wanted.filter { it !in slots }.forEach { id ->
            val free = slots.indexOfFirst { it == null }
            if (free >= 0) slots[free] = id
        }
        return slots
    }

    /** Watch alarm list for a plan: loaded slots enabled at their time, other slots disabled. */
    fun toWatchAlarms(plan: List<String?>, pool: List<PhoneAlarm>, watch: List<Alarm>): List<Alarm> {
        val byId = pool.associateBy { it.id }
        return watch.mapIndexed { i, w ->
            if (i >= SLOTS) w // slots beyond the usable four are left as they are
            else byId[plan.getOrNull(i)]?.let { a -> w.copy(hour = a.hour, minute = a.minute, enabled = true, oneTime = false, snooze = a.snooze) }
                ?: w.copy(enabled = false)
        }
    }

    /** Sound mode for the alarm that governs now (see [AlarmDayRules.desiredSoundMode]). */
    fun desiredSoundMode(pool: List<PhoneAlarm>, now: LocalDateTime): SoundMode? {
        if (pool.none { it.enabled && it.vibeOnly }) return null
        val recent = pool.mapNotNull { a -> lastRing(a, now)?.takeIf { it.plusMinutes(HOLD_MIN).isAfter(now) }?.let { a to it } }
            .maxByOrNull { it.second }
        val next = pool.mapNotNull { a -> nextRing(a, now)?.let { a to it } }.minByOrNull { it.second }
        val governing = when {
            recent == null -> next
            next != null && next.second.isBefore(recent.second.plusMinutes(HOLD_MIN)) -> next
            else -> recent
        } ?: return null
        return if (governing.first.vibeOnly) SoundMode.VIBE_ONLY else SoundMode.CUSTOM
    }
}
