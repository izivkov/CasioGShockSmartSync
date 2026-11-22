package org.avmedia.gshockGoogleSync.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRowsTest {
    @Test fun aLaterSmallerCounterDoesNotLowerTheDay() {
        val old = DayRow("2026-11-20", steps = 6000, kcal = 300, hrMax = 130, hrMin = 50)
        val partial = DayRow("2026-11-20", steps = 4000, kcal = 250, hrMax = 120, hrMin = 55)
        val m = partial.over(old)
        assertEquals(6000, m.steps)
        assertEquals(300, m.kcal)
        assertEquals(130, m.hrMax)
        assertEquals(50, m.hrMin)
    }

    @Test fun aGapInTheNewRowKeepsTheOldValueAndStaysNullWhenNeverRead() {
        val old = DayRow("2026-11-20", steps = 100, hrRest = 58)
        val m = DayRow("2026-11-20", steps = 150).over(old)
        assertEquals(150, m.steps)
        assertEquals(58, m.hrRest)
        assertNull(m.kcal)
        assertNull(DayRow("2026-11-21").over(null).steps)
    }

    @Test fun nightFieldsTakeTheNewestAndKeepEarlierOnesForGaps() {
        val old = NightRow("2026-11-20", score = 70, stages = "a,deep", totalMin = 400)
        val m = NightRow("2026-11-20", score = 82, stages = null).over(old)
        assertEquals(82, m.score)
        assertEquals("a,deep", m.stages)
        assertEquals(400, m.totalMin)
    }

    @Test fun hourStepsOnlyGrowAndHeartRateKeepsEarlier() {
        val m = HourRow("2026-11-20", 9, steps = 100).over(HourRow("2026-11-20", 9, steps = 300, hr = 70))
        assertEquals(300, m.steps)
        assertEquals(70, m.hr)
    }
}
