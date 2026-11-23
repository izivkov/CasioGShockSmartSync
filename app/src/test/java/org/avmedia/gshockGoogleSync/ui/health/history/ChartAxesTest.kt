package org.avmedia.gshockGoogleSync.ui.health.history

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The axes every history chart gets from the one shared drawing: round ticks, three to five of them, and the x labels. */
class ChartAxesTest {
    @Test fun stepsAreOneTwoOrFiveTimesAPowerOfTen() {
        assertEquals(50f, niceStep(100f), 0.001f)
        assertEquals(100f, niceStep(300f), 0.001f)
        assertEquals(1f, niceStep(4f), 0.001f)
        assertEquals(10f, niceStep(26f), 0.001f)
        assertEquals(2000f, niceStep(8000f), 0.001f)
        assertEquals(0.5f, niceStep(1.5f), 0.001f)
    }

    @Test fun everyScaleHasThreeToFiveRoundTicks() {
        val ranges = listOf(0f to 300f, 0f to 12000f, 0f to 9.4f, 57f to 83f, 40f to 190f, 280f to 420f, 0f to 1f, 0f to 8.3f)
        ranges.forEach { r ->
            val kind = if (r.first == 0f) ChartKind.BARS else ChartKind.LINES
            val scale = chartScale(r, kind)!!
            assertTrue("$r gave ${scale.ticks}", scale.ticks.size in 3..5)
            assertTrue(scale.low <= r.first && scale.high >= r.second)
            scale.ticks.forEach { t -> assertEquals(0f, (t / scale.step) - Math.round(t / scale.step), 0.001f) }
        }
    }

    @Test fun barsStartAtZeroAndLinesSnapOutwards() {
        val bars = chartScale(0f to 7234f, ChartKind.BARS)!!
        assertEquals(0f, bars.low, 0f)
        assertEquals(listOf(0f, 2000f, 4000f, 6000f, 8000f), bars.ticks)
        val lines = chartScale(57f to 83f, ChartKind.LINES)!!
        assertEquals(listOf(50f, 60f, 70f, 80f, 90f), lines.ticks)
    }

    @Test fun noDataNoScaleAndAFlatLineStillHasAnAxis() {
        assertNull(chartScale(null, ChartKind.BARS))
        val flat = chartScale(60f to 60f, ChartKind.LINES)!!
        assertTrue(flat.high > flat.low)
        assertTrue(flat.ticks.size >= 2)
    }

    @Test fun tickTextHasNoNeedlessDecimal() {
        assertEquals("2000", tickText(2000f, 2000f))
        assertEquals("2.5", tickText(2.5f, 0.5f))
        assertEquals("3", tickText(3f, 0.5f))
        assertEquals("5:30", paceText(330f))
    }

    @Test fun theWeekHasWeekdaysAndTheMonthADateEveryFiveDays() {
        val today = LocalDate.of(2026, 11, 22) // a Sunday
        val week = ChartLabels.days(today, 7, Locale.ENGLISH)
        assertEquals(7, week.size)
        assertEquals("Mon", week[0])
        assertEquals("Sun", week[6])
        val month = ChartLabels.days(today, 30, Locale.ENGLISH)
        assertEquals("22 Nov", month[29])
        assertEquals("17 Nov", month[24])
        assertEquals(6, month.count { it != null })
    }

    @Test fun theDayChartHasFourHourLabelsAndAWorkoutFourTimes() {
        assertEquals(listOf("0h", "6h", "12h", "18h"), ChartLabels.hours().filterNotNull())
        val short = ChartLabels.elapsed(List(12) { it * 150 }) // 0 to 1650 s
        assertEquals(listOf("0 min", "10 min", "20 min", "27 min"), short.filterNotNull())
        val long = ChartLabels.elapsed(List(12) { it * 400 }) // 0 to 4400 s
        assertEquals("1:13", long.filterNotNull().last())
        assertTrue(ChartLabels.elapsed(List(5) { null }).isEmpty())
    }
}
