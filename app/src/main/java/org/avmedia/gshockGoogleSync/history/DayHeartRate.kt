package org.avmedia.gshockGoogleSync.history

import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.HeartRateSlot
import org.avmedia.gshockapi.model.StepCounterData

/**
 * The heart rate curve of one day, like [TodayHours] for the steps: the stored 2-minute slots of the day with the newest
 * transfer laid over them (a read carries only the slots since the last read), nothing after now. Pure.
 */
object DayHeartRate {
    /** The slots per 5 minutes that Home Assistant gets (288 per day). */
    const val HA_SLOT_MINUTES = 5
    const val HA_SLOTS = 24 * 60 / HA_SLOT_MINUTES

    private fun minute(t: LocalDateTime) = t.hour * 60 + t.minute

    /** The slots of [day], oldest first: stored, then [newest] (the newest read wins a slot), none that start after [now]. */
    fun merge(newest: List<HeartRateSlot>, stored: List<HrSlotRow>, day: LocalDate, now: LocalDateTime): List<HeartRateSlot> {
        val byMinute = sortedMapOf<Int, Int>()
        stored.filter { it.date == day.toString() && it.minute in 0 until 24 * 60 }.forEach { byMinute[it.minute] = it.bpm }
        newest.filter { it.time.toLocalDate() == day }.forEach { byMinute[minute(it.time)] = it.bpm }
        return byMinute.map { (m, bpm) -> HeartRateSlot(day.atStartOfDay().plusMinutes(m.toLong()), bpm) }
            .filter { !it.time.isAfter(now) }
    }

    /** [fresh] with its heart rate slots replaced by the merged day of its read. */
    fun merge(fresh: StepCounterData, stored: List<HrSlotRow>, now: LocalDateTime = LocalDateTime.now()): StepCounterData {
        val day = fresh.timestamp?.toLocalDate() ?: return fresh
        val h = fresh.h5600 ?: return fresh
        return fresh.copy(h5600 = h.copy(heartRateSlots = merge(h.heartRateSlots, stored, day, now)))
    }

    /** The rows to store for a transfer: one per slot and day. */
    fun rows(slots: List<HeartRateSlot>): List<HrSlotRow> =
        slots.map { HrSlotRow(it.time.toLocalDate().toString(), minute(it.time), it.bpm) }.distinctBy { it.date to it.minute }

    /** 288 values for the day of [slots]: the rounded mean of the slots that start in each 5 minutes, null where none. */
    fun perFiveMinutes(slots: List<HeartRateSlot>): List<Int?> {
        val groups = slots.groupBy { minute(it.time) / HA_SLOT_MINUTES }
        return List(HA_SLOTS) { i -> groups[i]?.let { g -> Math.round(g.sumOf { it.bpm }.toDouble() / g.size).toInt() } }
    }

    /** The highest slot; the earliest one when several share the value. */
    fun highest(slots: List<HeartRateSlot>): HeartRateSlot? = slots.minWithOrNull(compareByDescending<HeartRateSlot> { it.bpm }.thenBy { it.time })

    /** The lowest slot; the earliest one when several share the value. */
    fun lowest(slots: List<HeartRateSlot>): HeartRateSlot? = slots.minWithOrNull(compareBy<HeartRateSlot> { it.bpm }.thenBy { it.time })
}
