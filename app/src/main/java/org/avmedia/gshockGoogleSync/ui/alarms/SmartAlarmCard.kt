package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockapi.model.SmartAlarm

private val WINDOWS = listOf(15, 30, 45, 60)

/** DW-H5600 smart alarm card. Tapping it opens the editor. */
@Composable
fun SmartAlarmCard(viewModel: SmartAlarmViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = state.alarm != null) { editing = true }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(id = R.string.smart_alarm), style = MaterialTheme.typography.titleMedium)
            Text(text = summary(state.alarm, state.loaded), style = MaterialTheme.typography.bodyMedium)
            state.error?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    val alarm = state.alarm
    if (editing && alarm != null) {
        SmartAlarmDialog(
            alarm = alarm,
            onDismiss = { editing = false },
            onSave = {
                viewModel.save(it)
                editing = false
            },
        )
    }
}

private fun summary(alarm: SmartAlarm?, loaded: Boolean): String = when {
    alarm != null -> String.format(
        "%02d:%02d, %s, wake up to %d min earlier, snooze %s",
        alarm.hour,
        alarm.minute,
        if (alarm.enabled) "on" else "off",
        alarm.windowMin,
        if (alarm.snooze) "on" else "off",
    )
    loaded -> "Not set"
    else -> "Reading from the watch..."
}

@Composable
private fun SmartAlarmDialog(
    alarm: SmartAlarm,
    onDismiss: () -> Unit,
    onSave: (SmartAlarm) -> Unit,
) {
    var hour by remember { mutableStateOf(alarm.hour.toString()) }
    var minute by remember { mutableStateOf(alarm.minute.toString()) }
    var enabled by remember { mutableStateOf(alarm.enabled) }
    var snooze by remember { mutableStateOf(alarm.snooze) }
    var windowMin by remember { mutableStateOf(alarm.windowMin.takeIf { it in WINDOWS } ?: WINDOWS.first()) }

    val parsedHour = hour.toIntOrNull()?.takeIf { it in 0..23 }
    val parsedMinute = minute.toIntOrNull()?.takeIf { it in 0..59 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.smart_alarm)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.take(2) },
                        label = { Text("Hour") },
                        singleLine = true,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.take(2) },
                        label = { Text("Minute") },
                        singleLine = true,
                    )
                }
                Text("Wake up before", style = MaterialTheme.typography.titleSmall)
                WINDOWS.forEach { w ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { windowMin = w },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = windowMin == w, onClick = null)
                        Text("$w minutes", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { enabled = !enabled },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = enabled, onCheckedChange = null)
                    Text("On", modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { snooze = !snooze },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = snooze, onCheckedChange = null)
                    Text("Snooze", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedHour != null && parsedMinute != null,
                onClick = {
                    onSave(
                        SmartAlarm(
                            hour = parsedHour!!,
                            minute = parsedMinute!!,
                            enabled = enabled,
                            snooze = snooze,
                            windowMin = windowMin,
                        ),
                    )
                },
            ) { Text(stringResource(id = R.string.smart_alarm_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.smart_alarm_cancel)) }
        },
    )
}
