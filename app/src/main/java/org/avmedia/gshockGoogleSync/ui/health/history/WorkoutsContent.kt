package org.avmedia.gshockGoogleSync.ui.health.history

import AppText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard

/**
 * Workouts of the DW-H5600 on the Health screen: one "Last workout" card while a workout is stored (nothing before);
 * a tap opens the list, a row opens one workout with laps and a heart-rate and pace chart.
 */
@Composable
fun WorkoutsContent(ui: WorkoutsUi, events: WorkoutEvents, modifier: Modifier = Modifier) {
    val last = ui.workouts.firstOrNull() ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val open = ui.selected
        when {
            open != null -> WorkoutDetailCard(open, ui, events)
            ui.listOpen -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText("Workouts", fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { events.onOpenList(false) }) { Text("Close") }
                }
                ui.workouts.take(50).forEach { WorkoutRowCard(it, ui, events) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("Export CSV", events.onExportCsv, Modifier.weight(1f))
                    AppButton("Export JSON", events.onExportJson, Modifier.weight(1f))
                }
                if (ui.exportNote.isNotBlank()) AppText(ui.exportNote, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }
            else -> LastWorkoutCard(last, ui, events)
        }
    }
}

@Composable
private fun LastWorkoutCard(w: WorkoutRow, ui: WorkoutsUi, events: WorkoutEvents) {
    AppCard(Modifier.fillMaxWidth().clickable { events.onOpenList(true) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText("Last workout", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(w.sportName, fontSize = 18.sp, modifier = Modifier.weight(1f))
                AppText(WorkoutText.start(w.startMs, ui.zone), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppText(WorkoutText.lastLine(w), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutRowCard(w: WorkoutRow, ui: WorkoutsUi, events: WorkoutEvents) {
    AppCard(Modifier.fillMaxWidth().clickable { events.onSelect(w) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(w.sportName, fontSize = 16.sp, modifier = Modifier.weight(1f))
                AppText(WorkoutText.start(w.startMs, ui.zone), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppText(WorkoutText.line(w), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutDetailCard(d: WorkoutDetail, ui: WorkoutsUi, events: WorkoutEvents) {
    val w = d.workout
    val grid = MaterialTheme.colorScheme.outlineVariant
    val gap = MaterialTheme.colorScheme.outline
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("${w.sportName}, ${WorkoutText.start(w.startMs, ui.zone)}", fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { events.onSelect(null) }) { Text("Close") }
            }
            WorkoutText.lines(w).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth()) {
                    AppText(k, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    AppText(v, fontSize = 14.sp)
                }
            }
            WorkoutText.unitsNote(w)?.let { AppText(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val xLabels = ChartLabels.elapsed(WorkoutText.series(d.samples) { it.elapsedSec }.map { it?.toInt() })
            val hr = WorkoutText.series(d.samples) { it.hr }
            if (hr.any { it != null }) {
                AppText("Heart rate (bpm)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryChart(listOf(ChartSeries(hr, MaterialTheme.colorScheme.error)), ChartKind.LINES, ChartAxes("bpm", xLabels), grid = grid, gapColor = gap)
            }
            val pace = WorkoutText.paceSeries(d)
            if (pace.any { it != null }) {
                AppText("Pace (min/km)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryChart(
                    listOf(ChartSeries(pace, MaterialTheme.colorScheme.primary)), ChartKind.LINES,
                    ChartAxes("min/km", xLabels, yFormat = { v, _ -> paceText(v) }), grid = grid, gapColor = gap,
                )
            }
            if (d.laps.isNotEmpty()) {
                AppText("Laps", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
                d.laps.forEach { l ->
                    Row(Modifier.fillMaxWidth()) {
                        AppText("${l.idx + 1}${if (l.manual) " (manual)" else ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
                        AppText(WorkoutText.lapLine(l), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
