package org.avmedia.gshockGoogleSync.history

import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockGoogleSync.homeassistant.HaSensors
import org.avmedia.gshockGoogleSync.watchvalues.HealthVariables
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before: HA `steps_by_hour` held only the newest transfer (hours 13 to 15 in one read, only 16 in the next), and
 * the Health screen showed only hour 17 after a read, a stored day with values after now before it.
 */
class TodayHoursTest {
    private val day = LocalDate.of(2026, 11, 23)

    private fun transfer(at: LocalDateTime, vararg hours: Pair<Int, Int>): StepCounterData {
        val byHour = List(24) { h -> hours.firstOrNull { it.first == h }?.second }
        return StepCounterData(
            timestamp = at, hourlySteps = byHour, dailyHistory = emptyList(), currentDaySteps = 2445,
            hourlyByHour = byHour, h5600 = H5600HealthData(stepGoal = 8000),
        )
    }

    /** What HistoryStore keeps of a stored transfer (the hour rows). */
    private fun stored(data: StepCounterData): List<HourRow> = HistoryMapper.map(data).hours

    @Test fun twoPartialTransfersGiveOneDay() {
        val first = TodayHours.merge(transfer(day.atTime(15, 50), 13 to 242, 14 to 518, 15 to 0), emptyList(), day.atTime(15, 50))
        val second = TodayHours.merge(transfer(day.atTime(16, 19), 16 to 122), stored(first), day.atTime(16, 19))
        assertEquals(listOf(242, 518, 0, 122), second.hourlyByHour.subList(13, 17))
        assertEquals(second.hourlyByHour, second.hourlySteps)
        assertTrue((0..12).all { second.hourlyByHour[it] == null })
    }

    @Test fun aNewDayStartsEmpty() {
        val yesterday = TodayHours.merge(transfer(day.atTime(22, 0), 20 to 900, 21 to 400), emptyList(), day.atTime(22, 0))
        val next = day.plusDays(1)
        val today = TodayHours.merge(transfer(next.atTime(0, 10), 0 to 12), stored(yesterday), next.atTime(0, 10))
        assertEquals(12, today.hourlyByHour[0])
        assertTrue((1..23).all { today.hourlyByHour[it] == null })
    }

    @Test fun hoursAfterNowAreEmpty() {
        val leaked = listOf(HourRow(day.toString(), 9, steps = 1052), HourRow(day.toString(), 23, steps = 179), HourRow(day.toString(), 18, steps = 0))
        val merged = TodayHours.merge(transfer(day.atTime(17, 53), 17 to 123), leaked, day.atTime(17, 53))
        assertEquals(1052, merged.hourlyByHour[9])
        assertEquals(123, merged.hourlyByHour[17])
        assertTrue((18..23).all { merged.hourlyByHour[it] == null })
        val screen = StoredHealth.stepData(listOf(DayRow(day.toString(), steps = 2926)), now = day.atTime(17, 53)) { leaked }!!
        assertTrue((18..23).all { screen.hourlySteps[it] == null })
    }

    @Test fun theScreenAndHomeAssistantShowTheSameDay() {
        val first = TodayHours.merge(transfer(day.atTime(15, 50), 13 to 242, 14 to 518), emptyList(), day.atTime(15, 50))
        val rows = stored(first)
        val now = day.atTime(16, 19)
        val merged = TodayHours.merge(transfer(now, 16 to 122), rows, now)
        val screen = StoredHealth.stepData(listOf(DayRow(day.toString(), steps = 2445)), now = now) { stored(merged) }!!
        assertEquals(HealthVariables.values(merged)["health.steps_by_hour"], screen.hourlySteps)
    }

    /** A read with no new hourly slots must still send the whole stored day with steps_today. */
    @Test fun aReadWithNoNewHoursStillSendsTheStoredDay() {
        val first = TodayHours.merge(transfer(day.atTime(20, 15), 13 to 242, 14 to 518, 20 to 18), emptyList(), day.atTime(20, 15))
        val now = day.atTime(21, 26)
        val empty = TodayHours.merge(transfer(now), stored(first), now)
        val values = HealthVariables.values(empty)
        val states = HaSensors.states(values)
        val attrs = HaSensors.attributes(values)
        assertTrue("steps_today" in states)
        assertEquals(first.hourlyByHour.subList(0, 21), (attrs.getValue("steps_today").getValue("steps_by_hour") as List<*>).subList(0, 21))
    }

    @Test fun theStoredDayKeepsTheLastGoalAndTime() {
        val last = StoredHealth.LastRead(day.atTime(17, 53), 8000)
        val screen = StoredHealth.stepData(listOf(DayRow(day.toString(), steps = 2926)), last, now = day.atTime(18, 0)) { emptyList() }!!
        assertEquals(8000, screen.h5600!!.stepGoal)
        assertEquals(day.atTime(17, 53), screen.timestamp)
    }

    /** Before, the chart drew the first to last hour with steps; one hour filled the width. */
    @Test fun theChartAlwaysHas24SlotsWithGapsForNoData() {
        val one = org.avmedia.gshockGoogleSync.ui.health.dwHourlySlots(List(24) { if (it == 17) 123 else null })
        assertEquals(24, one.size)
        assertEquals(123f, one[17])
        assertEquals(23, one.count { it == null })
        assertEquals(24, org.avmedia.gshockGoogleSync.ui.health.dwHourlySlots(listOf(5)).size)
    }
}
