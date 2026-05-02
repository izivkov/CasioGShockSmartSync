package com.beamburst.casswatch.ui.alarms

import org.avmedia.gshockapi.Alarm
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

object AlarmSchedulePlanner {
    fun applyWeeklySchedule(
        alarms: List<Alarm>,
        alarmDays: Map<Int, Set<DayOfWeek>>,
        now: LocalDateTime,
        viewMode: AlarmViewMode,
        firedAts: Map<Int, Long> = emptyMap()   // index → firedAt millis; empty = backward compat
    ): List<Alarm> {
        if (viewMode == AlarmViewMode.SIMPLE) return alarms

        val today = now.dayOfWeek
        val currentTime = now.toLocalTime()

        return alarms.mapIndexed { index, alarm ->
            val selectedDays = alarmDays[index]
            when {
                !alarm.enabled -> alarm                              // already off → pass through
                selectedDays.isNullOrEmpty() -> {
                    // Fire-once: no days selected
                    if (firedAts.containsKey(index)) {
                        alarm.copy(enabled = false)                  // consumed → disable on watch
                    } else {
                        alarm                                        // unfired → send enabled (watch fires daily)
                    }
                }
                else -> alarm.copy(
                    enabled = selectedDays.contains(today) && alarm.time().isAfter(currentTime)
                )
            }
        }
    }

    private fun Alarm.time(): LocalTime = LocalTime.of(hour, minute)
}
