package org.avmedia.gshockGoogleSync.ui.health

import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import org.avmedia.gshockGoogleSync.history.DayHeartRate
import org.avmedia.gshockGoogleSync.ui.health.history.ChartAxes
import org.avmedia.gshockGoogleSync.ui.health.history.ChartKind
import org.avmedia.gshockGoogleSync.ui.health.history.ChartLabels
import org.avmedia.gshockGoogleSync.ui.health.history.ChartMark
import org.avmedia.gshockGoogleSync.ui.health.history.ChartSeries
import org.avmedia.gshockGoogleSync.ui.health.history.HistoryChart
import org.avmedia.gshockapi.model.HeartRateSlot

/** What the day's heart rate chart shows: 288 values of 5 minutes and the highest and lowest 2-minute value with their times. Pure. */
internal object HeartRateDayText {
    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    class Mark(val index: Int, val bpm: Int, val label: String)

    fun values(slots: List<HeartRateSlot>): List<Float?> = DayHeartRate.perFiveMinutes(slots).map { it?.toFloat() }

    /** Max first, then min; none for an empty day, one when both are the same slot. */
    fun marks(slots: List<HeartRateSlot>): List<Mark> {
        val high = DayHeartRate.highest(slots) ?: return emptyList()
        val low = DayHeartRate.lowest(slots)!!
        fun index(s: HeartRateSlot) = (s.time.hour * 60 + s.time.minute) / DayHeartRate.HA_SLOT_MINUTES
        val out = listOf(Mark(index(high), high.bpm, "max ${high.bpm} at ${high.time.format(HM)}"))
        return if (low == high) out else out + Mark(index(low), low.bpm, "min ${low.bpm} at ${low.time.format(HM)}")
    }
}

/** The day's heart rate as a line from 0 to 24 h, the highest and lowest value marked with their times. Nothing for a day without values. */
@Composable
fun HeartRateDayChart(slots: List<HeartRateSlot>, modifier: Modifier = Modifier) {
    if (slots.isEmpty()) return
    val c = MaterialTheme.colorScheme
    val marks = HeartRateDayText.marks(slots)
    Column(modifier) {
        AppText("Heart rate today", fontSize = 13.sp, color = c.onSurfaceVariant)
        HistoryChart(
            listOf(ChartSeries(HeartRateDayText.values(slots), c.error)), ChartKind.LINES,
            ChartAxes("bpm", ChartLabels.daySlots(DayHeartRate.HA_SLOTS)), grid = c.outlineVariant, gapColor = c.outlineVariant,
            dots = false,
            marks = marks.mapIndexed { i, m -> ChartMark(m.index, m.bpm.toFloat(), m.label, if (i == 0) c.error else c.primary) },
        )
        AppText(marks.joinToString(", ") { it.label }, fontSize = 11.sp, color = c.onSurfaceVariant)
    }
}
