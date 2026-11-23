package org.avmedia.gshockGoogleSync.history

import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.StepCounterData

/**
 * The one day of hourly steps that the Health screen, and Home Assistant show: the stored hours of that day
 * with the newest transfer laid over them. The watch sends only the slots since the last read (log 2026-11-23: one read had
 * hours 13 to 15, the next only 16), so the newest transfer alone is a partial day. Hours after now stay empty.
 */
object TodayHours {
    /** 24 entries for [day]: stored hours of that day, then the newest transfer (the larger value wins), nothing after [now]. */
    fun hours(newest: List<Int?>, stored: List<HourRow>, day: LocalDate, now: LocalDateTime): List<Int?> {
        val out = MutableList<Int?>(HOURS) { null }
        stored.filter { it.date == day.toString() && it.hour in 0 until HOURS }.forEach { out[it.hour] = it.steps }
        newest.take(HOURS).forEachIndexed { h, s -> if (s != null) out[h] = out[h]?.let { maxOf(it, s) } ?: s }
        val today = now.toLocalDate()
        val firstEmpty = when {
            day.isBefore(today) -> HOURS
            day == today -> now.hour + 1
            else -> 0
        }
        for (h in firstEmpty until HOURS) out[h] = null
        return out
    }

    /** [fresh] with its hours replaced by the merged day. */
    fun merge(fresh: StepCounterData, stored: List<HourRow>, now: LocalDateTime = LocalDateTime.now()): StepCounterData {
        val day = fresh.timestamp?.toLocalDate() ?: return fresh
        val merged = hours(fresh.hourlyByHour, stored, day, now)
        return fresh.copy(hourlyByHour = merged, hourlySteps = merged)
    }

    private const val HOURS = 24
}
