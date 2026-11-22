package org.avmedia.gshockGoogleSync.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMapperTest {
    @Test fun oneTransferGivesOneRowPerDayHourAndNight() {
        val b = HistoryMapper.map(HistoryTestData.transfer())
        val day = b.days.single { it.date == "2026-11-20" }
        assertEquals(1, b.days.count { it.date == "2026-11-20" })
        assertEquals(5000, day.steps)
        assertEquals(310, day.kcal)
        assertEquals(62, day.activeMin)
        assertEquals(3600, day.walkSec)
        assertEquals(58, day.hrRest)
        assertEquals(97, day.spo2)
        assertEquals(4, day.stairsUp)
        assertEquals(listOf(8, 9, 10, 11, 12), b.hours.map { it.hour })
        assertEquals(75, b.hours.single { it.hour == 12 }.hr)
        assertNull(b.hours.single { it.hour == 12 }.steps)
        val night = b.nights.single()
        assertEquals("2026-11-20", night.date)
        assertEquals(82, night.score)
        assertEquals(90, night.deepMin)
        assertEquals("2026-11-20T00:10:00,light\n2026-11-20T01:00:00,deep\n2026-11-20T03:00:00,rem", night.stages)
    }

    @Test fun noStepsAreNotZero() {
        val b = HistoryMapper.map(HistoryTestData.transfer(steps = null, hours = emptyMap(), night = null))
        assertNull(b.days.single { it.date == "2026-11-20" }.steps)
        assertTrue(b.nights.isEmpty())
        assertTrue(b.hours.none { it.steps != null })
    }

    @Test fun anAbsentTransferAddsNothing() {
        val b = HistoryMapper.map(null)
        assertTrue(b.days.isEmpty() && b.hours.isEmpty() && b.nights.isEmpty())
    }
}
