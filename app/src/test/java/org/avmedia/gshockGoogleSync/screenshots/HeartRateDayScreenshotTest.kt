package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.avmedia.gshockGoogleSync.ui.health.HeartRateDayChart
import org.avmedia.gshockapi.model.HeartRateSlot
import org.junit.Test

/** The day's heart rate chart: a day up to 15:00 with a gap in the morning, max and min marked. */
class HeartRateDayScreenshotTest : ScreenshotTest() {
    private val day = LocalDate.of(2026, 11, 23)
    private val slots = (0 until 15 * 30).filter { it !in 200..230 }.map { i ->
        val bpm = when {
            i < 210 -> 52 + (i * 7) % 9
            i in 300..330 -> 120 + (i - 300)
            else -> 70 + (i * 13) % 15
        }
        HeartRateSlot(day.atStartOfDay().plusMinutes(2L * i), bpm)
    }

    @Test fun dayLight() = snap("heart_rate_day", false) { HeartRateDayChart(slots, Modifier.padding(8.dp)) }
    @Test fun dayDark() = snap("heart_rate_day", true) { HeartRateDayChart(slots, Modifier.padding(8.dp)) }
}
