package org.avmedia.gshockGoogleSync.ui.health.history

import AppText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.avmedia.gshockGoogleSync.history.DayRow
import org.avmedia.gshockGoogleSync.history.HistorySeries
import org.avmedia.gshockGoogleSync.history.NightRow
import org.avmedia.gshockGoogleSync.history.SleepTimeline
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard

/** The history part of the Health screen: week and month charts, the calendar, a day or night, and the export. */
@Composable
fun HistoryContent(ui: HistoryUi, events: HistoryEvents, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppText("History", fontSize = 20.sp, modifier = Modifier.weight(1f))
            HistoryRange.entries.forEach { r ->
                val on = r == ui.range
                TextButton(onClick = { events.onRange(r) }) {
                    Text(r.label, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val n = ui.range.days
        val grid = MaterialTheme.colorScheme.outlineVariant
        val gap = MaterialTheme.colorScheme.outline
        val steps = HistorySeries.days(ui.days, ui.today, n) { it.steps }
        val rest = HistorySeries.days(ui.days, ui.today, n) { it.hrRest }
        val max = HistorySeries.days(ui.days, ui.today, n) { it.hrMax }
        val sleep = HistorySeries.nights(ui.nights, ui.today, n) { it.totalMin }
        ChartCard("Steps", summary(steps, "steps"), ui, n) {
            HistoryChart(listOf(ChartSeries(steps, MaterialTheme.colorScheme.primary)), ChartKind.BARS, grid = grid, gapColor = gap)
        }
        ChartCard("Heart rate", summary(rest, "bpm resting"), ui, n) {
            HistoryChart(
                listOf(ChartSeries(max, MaterialTheme.colorScheme.error), ChartSeries(rest, MaterialTheme.colorScheme.primary)),
                ChartKind.LINES, grid = grid, gapColor = gap,
            )
        }
        ChartCard("Sleep", summary(sleep.map { it?.div(60f) }, "h", decimals = 1), ui, n) {
            HistoryChart(listOf(ChartSeries(sleep, MaterialTheme.colorScheme.tertiary)), ChartKind.BARS, grid = grid, gapColor = gap)
        }
        CalendarCard(ui, events)
        ui.selected?.let { DetailCard(it, ui, events) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Export CSV", events.onExportCsv, Modifier.weight(1f))
            AppButton("Export JSON", events.onExportJson, Modifier.weight(1f))
        }
        if (ui.exportNote.isNotBlank()) AppText(ui.exportNote, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
    }
}

/** "6 420 steps average" or "no data": the average of the days that have a value. */
internal fun summary(values: List<Float?>, unit: String, decimals: Int = 0): String {
    val v = values.filterNotNull()
    if (v.isEmpty()) return "no data"
    val avg = v.average()
    val text = if (decimals == 0) Math.round(avg).toString() else String.format(Locale.US, "%.${decimals}f", avg)
    return "$text $unit, average of ${v.size} of ${values.size} days"
}

@Composable
private fun ChartCard(title: String, summary: String, ui: HistoryUi, n: Int, chart: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
                AppText(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            chart()
            Row(Modifier.fillMaxWidth()) {
                val f = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                AppText(ui.today.minusDays(n - 1L).format(f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                AppText(ui.today.format(f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CalendarCard(ui: HistoryUi, events: HistoryEvents) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { events.onMonth(-1) }) { Text("<") }
                AppText(
                    ui.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ui.month.year,
                    fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { events.onMonth(1) }, enabled = ui.month < java.time.YearMonth.from(ui.today)) { Text(">") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                    AppText(d, fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
            HistorySeries.calendar(ui.month, ui.days, ui.nights).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        if (cell == null) Box(Modifier.weight(1f).height(40.dp))
                        else DayCell(cell.date, cell.hasDay, cell.hasNight, cell.date == ui.selected, { events.onSelect(cell.date) })
                    }
                }
            }
            AppText("Filled: a day with data. Dot: a night with data. Empty: nothing read.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate, hasDay: Boolean, hasNight: Boolean, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.weight(1f).height(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (hasDay) primary.copy(alpha = if (selected) 1f else 0.35f) else if (selected) MaterialTheme.colorScheme.outlineVariant else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            AppText(date.dayOfMonth.toString(), fontSize = 13.sp, color = if (hasDay && selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        if (hasNight) Box(Modifier.align(Alignment.BottomCenter).size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
    }
}

@Composable
private fun DetailCard(date: LocalDate, ui: HistoryUi, events: HistoryEvents) {
    val day = ui.days.firstOrNull { it.date == date.toString() }
    val night = ui.nights.firstOrNull { it.date == date.toString() }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())), fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { events.onSelect(null) }) { Text("Close") }
            }
            if (day == null && night == null) AppText("Nothing was read for this day.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            day?.let { d -> dayLines(d).forEach { (k, v) -> Line(k, v) } }
            if (ui.selectedHours.any { it.steps != null }) {
                val hours = List(24) { h -> ui.selectedHours.firstOrNull { it.hour == h }?.steps?.toFloat() }
                AppText("Steps per hour", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryChart(
                    listOf(ChartSeries(hours, MaterialTheme.colorScheme.primary)), ChartKind.BARS,
                    grid = MaterialTheme.colorScheme.outlineVariant, gapColor = MaterialTheme.colorScheme.outline,
                )
            }
            night?.let { n ->
                AppText("Night", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
                nightLines(n).forEach { (k, v) -> Line(k, v) }
                val segs = SleepTimeline.segments(n.stages, n.wokeUp)
                if (segs.isNotEmpty()) StageStrip(segs.map { Triple(it.stage, it.startMin, it.endMin) })
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        AppText(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        AppText(value, fontSize = 14.sp)
    }
}

internal fun dayLines(d: DayRow): List<Pair<String, String>> = listOfNotNull(
    d.steps?.let { "Steps" to "$it" }, d.distanceM?.let { "Distance" to "%.1f km".format(Locale.US, it / 1000f) },
    d.kcal?.let { "Energy" to "$it kcal" }, d.activeMin?.let { "Active" to "$it min" },
    d.walkSec?.let { "Walking" to "${it / 60} min" }, d.runSec?.let { "Running" to "${it / 60} min" }, d.stillSec?.let { "Still" to "${it / 60} min" },
    d.hrRest?.let { "Resting heart rate" to "$it bpm" }, d.hrMin?.let { "Lowest heart rate" to "$it bpm" }, d.hrMax?.let { "Highest heart rate" to "$it bpm" },
    d.spo2?.let { "Blood oxygen" to "$it %" }, d.stairsUp?.let { "Floors up" to "$it" }, d.stairsDown?.let { "Floors down" to "$it" },
)

internal fun nightLines(n: NightRow): List<Pair<String, String>> = listOfNotNull(
    n.score?.let { "Sleep score" to "$it" }, n.rechargeStatus?.let { "Nightly Recharge" to "$it" },
    n.fellAsleep?.let { "Fell asleep" to it.substringAfter('T').take(5) }, n.wokeUp?.let { "Woke up" to it.substringAfter('T').take(5) },
    n.totalMin?.let { "Total" to "${it / 60} h ${it % 60} min" }, n.deepMin?.let { "Deep" to "$it min" },
    n.lightMin?.let { "Light" to "$it min" }, n.remMin?.let { "REM" to "$it min" }, n.awakeMin?.let { "Awake" to "$it min" },
    n.hr?.let { "Heart rate" to "$it bpm" }, n.breathing?.let { "Breathing" to "%.1f per min".format(Locale.US, it) },
)

/** The stage timeline as one coloured strip. */
@Composable
private fun StageStrip(segments: List<Triple<String, Int, Int>>) {
    val colors = mapOf(
        "awake" to MaterialTheme.colorScheme.error, "rem" to MaterialTheme.colorScheme.secondary,
        "light" to MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f), "deep" to MaterialTheme.colorScheme.primary,
    )
    val fallback = MaterialTheme.colorScheme.outline
    val total = (segments.maxOf { it.third } - segments.minOf { it.second }).coerceAtLeast(1)
    val origin = segments.minOf { it.second }
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        segments.forEach { (stage, a, b) ->
            val x = (a - origin) / total.toFloat() * size.width
            drawRect(colors[stage] ?: fallback, Offset(x, 0f), Size((b - a) / total.toFloat() * size.width, size.height))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { (name, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                AppText(" $name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
