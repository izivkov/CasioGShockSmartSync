package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryChartTest {
    private fun s(vararg v: Float?) = ChartSeries(v.toList(), Color.Red)

    @Test fun barsStartAtZeroAndGapsDoNotCount() {
        assertEquals(0f to 300f, chartRange(listOf(s(100f, null, 300f)), ChartKind.BARS))
    }

    @Test fun linesFitTheDataWithAMargin() {
        assertEquals(57f to 83f, chartRange(listOf(s(60f, null, 80f)), ChartKind.LINES))
    }

    @Test fun noValueNoRange() {
        assertNull(chartRange(listOf(s(null, null)), ChartKind.BARS))
        assertNull(chartRange(emptyList(), ChartKind.LINES))
        assertNotNull(chartRange(listOf(s(0f)), ChartKind.BARS))
    }

    @Test fun fractionStaysInsideTheChart() {
        assertEquals(0.5f, chartFraction(150f, 0f, 300f), 0.0001f)
        assertEquals(1f, chartFraction(999f, 0f, 300f), 0f)
        assertEquals(0f, chartFraction(-5f, 0f, 300f), 0f)
        assertEquals(0.5f, chartFraction(1f, 1f, 1f), 0f)
    }
}
