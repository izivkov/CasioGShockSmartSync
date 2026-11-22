package org.avmedia.gshockGoogleSync.history

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySeriesTest {
    @Test fun missingDaysAndEmptyColumnsAreNullNotZero() {
        val rows = listOf(DayRow("2026-11-20", steps = 100), DayRow("2026-11-22", hrRest = 60))
        val v = HistorySeries.days(rows, LocalDate.of(2026, 11, 22), 4) { it.steps }
        assertEquals(listOf(null, null, 100f, null), v)
    }

    @Test fun nightSeriesFollowsTheSameRule() {
        val v = HistorySeries.nights(listOf(NightRow("2026-11-21", totalMin = 400)), LocalDate.of(2026, 11, 22), 3) { it.totalMin }
        assertEquals(listOf(null, 400f, null), v)
    }

    @Test fun calendarStartsOnMondayAndMarksDaysAndNights() {
        // 1 November 2026 is a Sunday: six empty cells first.
        val weeks = HistorySeries.calendar(YearMonth.of(2026, 11), listOf(DayRow("2026-11-20", steps = 1), DayRow("2026-11-21")), listOf(NightRow("2026-11-20")))
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(List(6) { null }, weeks[0].take(6))
        assertEquals(LocalDate.of(2026, 11, 1), weeks[0][6]?.date)
        val cells = weeks.flatten().filterNotNull()
        assertEquals(30, cells.size)
        val d20 = cells.single { it.date.dayOfMonth == 20 }
        assertTrue(d20.hasDay && d20.hasNight)
        assertFalse(cells.single { it.date.dayOfMonth == 21 }.hasDay)
        assertFalse(cells.single { it.date.dayOfMonth == 5 }.hasNight)
    }

    @Test fun summaryOfNothingSaysNoData() {
        assertEquals("no data", org.avmedia.gshockGoogleSync.ui.health.history.summary(listOf(null, null), "steps"))
        assertEquals("150 steps, average of 2 of 3 days", org.avmedia.gshockGoogleSync.ui.health.history.summary(listOf(100f, null, 200f), "steps"))
    }
}
