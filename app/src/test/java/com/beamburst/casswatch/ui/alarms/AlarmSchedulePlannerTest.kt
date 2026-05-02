package com.beamburst.casswatch.ui.alarms

import org.avmedia.gshockapi.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun `weekly mode fire-once unfired sends alarm enabled`() {
        // No days selected, alarm enabled, no firedAt → goes to watch enabled (all-days semantic)
        val alarm = Alarm(hour = 23, minute = 0, enabled = true, hasHourlyChime = false, name = null)
        val result = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = listOf(alarm),
            alarmDays = mapOf(0 to emptySet()),          // empty = fire-once
            now = LocalDateTime.of(2026, 5, 2, 10, 0),
            viewMode = AlarmViewMode.WEEKLY,
            firedAts = emptyMap()                         // not yet fired
        )
        assertTrue("fire-once unfired alarm must be sent enabled", result[0].enabled)
    }

    @Test
    fun `weekly mode fire-once consumed sends alarm disabled`() {
        val alarm = Alarm(hour = 9, minute = 0, enabled = true, hasHourlyChime = false, name = null)
        val result = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = listOf(alarm),
            alarmDays = mapOf(0 to emptySet()),
            now = LocalDateTime.of(2026, 5, 2, 10, 0),
            viewMode = AlarmViewMode.WEEKLY,
            firedAts = mapOf(0 to 1746172800000L)         // has been fired
        )
        assertFalse("fire-once consumed alarm must be sent disabled", result[0].enabled)
    }

    @Test
    fun `weekly mode fire-once explicitly disabled passes through disabled`() {
        val alarm = Alarm(hour = 9, minute = 0, enabled = false, hasHourlyChime = false, name = null)
        val result = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = listOf(alarm),
            alarmDays = mapOf(0 to emptySet()),
            now = LocalDateTime.of(2026, 5, 2, 10, 0),
            viewMode = AlarmViewMode.WEEKLY,
            firedAts = emptyMap()
        )
        assertFalse("disabled fire-once alarm must stay disabled", result[0].enabled)
    }
}
