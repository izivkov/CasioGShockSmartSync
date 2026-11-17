package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.SoundMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
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
    /**
     * How long an alarm still "governs" after its ring time (stays on the watch, keeps its sound
     * mode, a one-time alarm stays on): the snooze repeats (up to 7 x 5 min) if snooze is on,
     * otherwise just the ring itself.
     */
    fun holdMin(a: PhoneAlarm): Long = if (a.snooze) AlarmDayRules.SNOOZE_HOLD_MIN else RING_MIN
    private const val RING_MIN = 1L

    /** End of the hold after [ring]; a ring whose snooze was stopped from the phone ends at once. */
    fun holdEnd(a: PhoneAlarm, ring: LocalDateTime): LocalDateTime =
        ring.plusMinutes(if (a.dismissedRing == ring) RING_MIN else holdMin(a))

    private fun inHold(a: PhoneAlarm, now: LocalDateTime): Boolean =
        lastRing(a, now)?.let { holdEnd(a, it).isAfter(now) } == true

    /**
     * Alarms that may be snoozing right now: snooze on, rang within the snooze window, not stopped
     * from the phone. The watch does not report snooze state, so this is the phone's best guess.
     * Their last ring time comes with them.
     */
    fun snoozing(pool: List<PhoneAlarm>, now: LocalDateTime): List<Pair<PhoneAlarm, LocalDateTime>> =
        pool.filter { it.snooze }.mapNotNull { a -> lastRing(a, now)?.takeIf { holdEnd(a, it).isAfter(now) }?.let { a to it } }

    /** Marks alarm [id]'s last ring as stopped (snooze cancelled on the watch). */
    fun dismiss(pool: List<PhoneAlarm>, id: String, now: LocalDateTime): List<PhoneAlarm> =
        pool.map { a -> if (a.id == id) lastRing(a, now)?.let { a.copy(dismissedRing = it) } ?: a else a }

    data class PhoneAlarm(
        val id: String = UUID.randomUUID().toString(),
        val hour: Int,
        val minute: Int,
        val enabled: Boolean = true,
        val days: Set<DayOfWeek> = AlarmDayRules.ALL_DAYS,
        val vibeOnly: Boolean = false,
        val label: String = "", // phone-side only; the watch has no alarm names
        val snooze: Boolean = true,
        /** 0 = normal alarm; 15/30/45/60 = smart alarm waking up to that many minutes earlier. */
        val smartWindowMin: Int = 0,
        /** One-time alarm: rings once on [onceDate] (ignores [days]), then switches itself off. */
        val once: Boolean = false,
        /** The day a one-time alarm is set for (see [arm]); null until armed. */
        val onceDate: LocalDate? = null,
        /** Ring whose snooze was stopped from the phone ([dismiss]): its hold ends right away. */
        val dismissedRing: LocalDateTime? = null,
    ) {
        val isSmart: Boolean get() = smartWindowMin > 0
        val minuteOfDay: Int get() = hour * 60 + minute
    }

    // ---- storage ----
    private const val PREFS = "h5600_prefs"
    private const val KEY_POOL = "alarm_pool_json"
    private const val KEY_LOADED = "alarm_pool_loaded_ids"
    private const val KEY_SMART_LOADED = "alarm_pool_smart_loaded_id"

    fun loadedSmartId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SMART_LOADED, null)

    fun saveLoadedSmartId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SMART_LOADED, id).apply()
    }

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
                        smartWindowMin = o.optInt("smartWindowMin", 0),
                        once = o.optBoolean("once", false),
                        onceDate = o.optString("onceDate", "").takeIf { it.isNotEmpty() }
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        dismissedRing = o.optString("dismissedRing", "").takeIf { it.isNotEmpty() }
                            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
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
                    .put("snooze", a.snooze)
                    .put("smartWindowMin", a.smartWindowMin)
                    .put("once", a.once)
                    .put("onceDate", a.onceDate?.toString() ?: "")
                    .put("dismissedRing", a.dismissedRing?.toString() ?: ""),
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

    /** Ring times of an alarm from 8 days back to 8 days ahead (a one-time alarm: its one ring). */
    private fun rings(a: PhoneAlarm, now: LocalDateTime): List<LocalDateTime> =
        if (!a.enabled) emptyList()
        else if (a.once) listOfNotNull(a.onceDate?.atTime(a.hour, a.minute))
        else (-8L..8L).map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in a.days }
            .map { it.atTime(a.hour, a.minute) }

    /**
     * Sets a one-time alarm for the next time its hh:mm comes round (today if still ahead,
     * otherwise tomorrow). Called when it is created, edited or switched back on. No-op for others.
     */
    fun arm(a: PhoneAlarm, now: LocalDateTime): PhoneAlarm {
        if (!a.once) return if (a.onceDate == null) a else a.copy(onceDate = null)
        val today = now.toLocalDate().atTime(a.hour, a.minute)
        return a.copy(onceDate = (if (today.isAfter(now)) today else today.plusDays(1)).toLocalDate())
    }

    /**
     * One-time alarms whose ring is over (rang more than [holdMin] minutes ago, so snooze is done)
     * are switched off. Returns the list unchanged if none expired.
     */
    fun expireOnce(pool: List<PhoneAlarm>, now: LocalDateTime): List<PhoneAlarm> =
        pool.map { a ->
            val ring = a.onceDate?.atTime(a.hour, a.minute)
            if (a.once && a.enabled && ring != null && !holdEnd(a, ring).isAfter(now)) a.copy(enabled = false) else a
        }

    fun nextRing(a: PhoneAlarm, now: LocalDateTime): LocalDateTime? = rings(a, now).firstOrNull { it.isAfter(now) }
    private fun lastRing(a: PhoneAlarm, now: LocalDateTime): LocalDateTime? = rings(a, now).lastOrNull { !it.isAfter(now) }

    /**
     * Which alarm goes in which watch slot. Keeps alarms in the slot they already occupy (so a
     * ringing / snoozing slot is not rewritten), keeps alarms that rang in the last [holdMin]
     * minutes (snooze), then fills free slots with the alarms due soonest within 24 h.
     */
    fun plan(allAlarms: List<PhoneAlarm>, current: List<String?>, now: LocalDateTime): List<String?> {
        val pool = allAlarms.filterNot { it.isSmart } // smart alarms use the watch's single smart slot
        val byId = pool.associateBy { it.id }
        val dueSoon = pool.mapNotNull { a -> nextRing(a, now)?.let { a to it } }
            .filter { (_, t) -> t.isBefore(now.plusHours(24)) }
            .sortedBy { it.second }.map { it.first.id }
        val snoozing = pool.filter { a ->
            inHold(a, now)
        }.map { it.id }
        val wanted = (snoozing + dueSoon).distinct().take(SLOTS)

        val slots = MutableList<String?>(SLOTS) { i -> current.getOrNull(i)?.takeIf { it in wanted && it in byId } }
        wanted.filter { it !in slots }.forEach { id ->
            val free = slots.indexOfFirst { it == null }
            if (free >= 0) slots[free] = id
        }
        return slots
    }

    /**
     * Which alarm goes into the watch's single smart-alarm slot: one that rang within the snooze
     * hold keeps it, otherwise the next smart alarm due within 24 h (none -> slot off).
     */
    fun planSmart(all: List<PhoneAlarm>, current: String?, now: LocalDateTime): String? {
        val smart = all.filter { it.isSmart }
        val snoozing = smart.filter { a -> inHold(a, now) }
        snoozing.firstOrNull { it.id == current }?.let { return it.id }
        snoozing.firstOrNull()?.let { return it.id }
        return smart.mapNotNull { a -> nextRing(a, now)?.let { a to it } }
            .filter { (_, t) -> t.isBefore(now.plusHours(24)) }
            .minByOrNull { it.second }?.first?.id
    }

    /** True if the watch already has what [wanted] asks for, in the fields the app manages. */
    fun sameOnWatch(wanted: List<Alarm>, watch: List<Alarm>): Boolean =
        wanted.size == watch.size && wanted.zip(watch).all { (x, w) ->
            x.enabled == w.enabled && (!x.enabled || (x.hour == w.hour && x.minute == w.minute &&
                (x.snooze == null || w.snooze == null || x.snooze == w.snooze)))
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

    /** Sound mode for the alarm that governs now: the most recent one within its [holdMin], else the next. */
    fun desiredSoundMode(pool: List<PhoneAlarm>, now: LocalDateTime): SoundMode? {
        if (pool.none { it.enabled && it.vibeOnly }) return null
        val recent = pool.mapNotNull { a -> lastRing(a, now)?.takeIf { holdEnd(a, it).isAfter(now) }?.let { a to it } }
            .maxByOrNull { it.second }
        // A smart alarm can ring up to its window early: treat its window start as its ring time.
        // Next = the alarm whose real ring is still ahead, ranked by when it may start ringing
        // (a smart alarm can wake up to its window early, so inside the window it already governs).
        val next = pool.mapNotNull { a ->
            rings(a, now).firstOrNull { it.isAfter(now) }?.let { a to it.minusMinutes(a.smartWindowMin.toLong()) }
        }.minByOrNull { it.second }
        val governing = when {
            recent == null -> next
            next != null && next.second.isBefore(holdEnd(recent.first, recent.second)) -> next
            else -> recent
        } ?: return null
        return if (governing.first.vibeOnly) SoundMode.VIBE_ONLY else SoundMode.CUSTOM
    }
}
