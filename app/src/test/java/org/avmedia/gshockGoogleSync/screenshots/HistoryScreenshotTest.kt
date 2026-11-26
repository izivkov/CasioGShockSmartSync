package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import java.time.LocalDate
import org.avmedia.gshockGoogleSync.history.DayRow
import org.avmedia.gshockGoogleSync.history.HourRow
import org.avmedia.gshockGoogleSync.history.NightRow
import org.avmedia.gshockGoogleSync.ui.health.history.HistoryContent
import org.avmedia.gshockGoogleSync.ui.health.history.HistoryEvents
import org.avmedia.gshockGoogleSync.ui.health.history.HistoryRange
import org.avmedia.gshockGoogleSync.ui.health.history.HistoryUi
import org.junit.Test

/** The Health history views: week, month, a day opened from the calendar, and nothing read yet. */
class HistoryScreenshotTest : ScreenshotTest() {
    private val today = LocalDate.of(2026, 11, 22)

    // Gaps on purpose: no read on the 4th, 9th, 10th and 15th day back, and no night on every third day.
    private val days = (0..29).filter { it % 5 != 4 }.map { back ->
        val d = today.minusDays(back.toLong())
        DayRow(
            d.toString(), steps = 3000 + (back * 937) % 7000, kcal = 200 + back * 7 % 150, activeMin = 20 + back % 40,
            hrRest = 54 + back % 9, hrMin = 48 + back % 5, hrMax = 110 + back * 3 % 40,
            walkSec = 2400 + back * 60, runSec = 300, stillSec = 20000, spo2 = 95 + back % 3,
        )
    }
    private val nights = (0..29).filter { it % 3 != 2 }.map { back ->
        val d = today.minusDays(back.toLong())
        NightRow(
            d.toString(), score = 60 + back * 7 % 35, rechargeStatus = 3, fellAsleep = d.atTime(0, 10).toString(), wokeUp = d.atTime(7, 0).toString(),
            totalMin = 360 + back * 11 % 90, deepMin = 80, lightMin = 230, remMin = 70, awakeMin = 10, hr = 52, breathing = 14.5f,
            stages = "${d}T00:10:00,light\n${d}T01:00:00,deep\n${d}T02:30:00,light\n${d}T04:00:00,rem\n${d}T05:00:00,awake\n${d}T05:10:00,light",
        )
    }
    private val hours = listOf(8 to 800, 9 to 1200, 10 to 1500, 12 to 400, 15 to 900, 18 to 2100).map { (h, s) -> HourRow(today.toString(), h, steps = s) }

    private val week = HistoryUi(days = days, nights = nights, range = HistoryRange.WEEK, today = today)
    private val month = week.copy(range = HistoryRange.MONTH)
    private val opened = week.copy(selected = today.minusDays(1), selectedHours = hours.map { it.copy(date = today.minusDays(1).toString()) })

    @Composable private fun Screen(ui: HistoryUi) =
        HistoryContent(ui, HistoryEvents(), Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp))

    @Test fun weekLight() = snap("history_week", false) { Screen(week) }
    @Test fun weekDark() = snap("history_week", true) { Screen(week) }
    @Test fun monthLight() = snap("history_month", false) { Screen(month) }
    @Test fun dayOpenLight() = snap("history_day", false) { Screen(opened) }
    @Test fun dayOpenDark() = snap("history_day", true) { Screen(opened) }
    @Test fun emptyLight() = snap("history_empty", false) { Screen(HistoryUi(today = today)) }
}
