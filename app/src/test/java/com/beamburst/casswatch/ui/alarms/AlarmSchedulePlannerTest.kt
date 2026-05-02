package com.beamburst.casswatch.ui.alarms

import org.avmedia.gshockapi.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class AlarmSchedulePlannerTest {

    @Test
    fun weeklyModeDisablesAlarmsForOtherDays() {
        val alarms = listOf(
            Alarm(11, 45, true, false, "Monday"),
            Alarm(13, 45, true, false, "Monday")
        )

        val planned = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = alarms,
            alarmDays = mapOf(
                0 to setOf(DayOfWeek.MONDAY),
                1 to setOf(DayOfWeek.MONDAY)
            ),
            now = LocalDateTime.of(2026, 5, 1, 16, 55),
            viewMode = AlarmViewMode.WEEKLY
        )

        assertEquals(listOf(false, false), planned.map { it.enabled })
    }

    @Test
    fun weeklyModeDisablesTodaysAlarmsAfterTheirTimePassed() {
        val alarms = listOf(
            Alarm(11, 15, true, false, "Friday"),
            Alarm(18, 0, true, false, "Friday")
        )

        val planned = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = alarms,
            alarmDays = mapOf(
                0 to setOf(DayOfWeek.FRIDAY),
                1 to setOf(DayOfWeek.FRIDAY)
            ),
            now = LocalDateTime.of(2026, 5, 1, 16, 55),
            viewMode = AlarmViewMode.WEEKLY
        )

        assertEquals(listOf(false, true), planned.map { it.enabled })
    }

    @Test
    fun simpleModeLeavesAlarmsUnchanged() {
        val alarms = listOf(
            Alarm(11, 15, true, false, "Daily"),
            Alarm(9, 3, false, false, "Off")
        )

        val planned = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = alarms,
            alarmDays = mapOf(0 to setOf(DayOfWeek.MONDAY)),
            now = LocalDateTime.of(2026, 5, 1, 16, 55),
            viewMode = AlarmViewMode.SIMPLE
        )

        assertEquals(alarms, planned)
    }
}
