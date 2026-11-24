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

/** Workouts of the DW-H5600: the list, and one opened workout with laps and a heart-rate and pace chart. */
@Composable
fun WorkoutsContent(ui: WorkoutsUi, events: WorkoutEvents, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText("Workouts", fontSize = 20.sp)
        val open = ui.selected
        if (open != null) WorkoutDetailCard(open, ui, events)
        else {
            AppButton(if (ui.downloading) "Reading workouts..." else "Read workouts from the watch", events.onDownload, Modifier.fillMaxWidth())
            if (ui.downloadNote.isNotBlank()) AppText(ui.downloadNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.workouts.isEmpty()) AppText("No workout downloaded yet.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.workouts.take(50).forEach { WorkoutRowCard(it, ui, events) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Export CSV", events.onExportCsv, Modifier.weight(1f))
                AppButton("Export JSON", events.onExportJson, Modifier.weight(1f))
            }
            AppButton("Share raw workout log", events.onShareRaw, Modifier.fillMaxWidth())
            if (ui.exportNote.isNotBlank()) AppText(ui.exportNote, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
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
            val xLabels = ChartLabels.elapsed(WorkoutText.series(d.samples) { it.elapsedSec }.map { it?.toInt() })
            val hr = WorkoutText.series(d.samples) { it.hr }
            if (hr.any { it != null }) {
                AppText("Heart rate (bpm)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryChart(listOf(ChartSeries(hr, MaterialTheme.colorScheme.error)), ChartKind.LINES, ChartAxes("bpm", xLabels), grid = grid, gapColor = gap)
            }
            val pace = WorkoutText.series(d.samples) { it.pace }
            if (pace.any { it != null }) {
                AppText("Pace (seconds per km, unconfirmed)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            AppText("Units marked unconfirmed are the likely ones from the watch's data format, not checked on the watch.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
