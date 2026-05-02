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
        viewMode: AlarmViewMode
    ): List<Alarm> {
        if (viewMode == AlarmViewMode.SIMPLE) return alarms

        val today = now.dayOfWeek
        val currentTime = now.toLocalTime()

        return alarms.mapIndexed { index, alarm ->
            val selectedDays = alarmDays[index]
            if (!alarm.enabled || selectedDays.isNullOrEmpty()) {
                alarm
            } else {
                alarm.copy(
                    enabled = selectedDays.contains(today) && alarm.time().isAfter(currentTime)
                )
            }
        }
    }

    private fun Alarm.time(): LocalTime = LocalTime.of(hour, minute)
}
