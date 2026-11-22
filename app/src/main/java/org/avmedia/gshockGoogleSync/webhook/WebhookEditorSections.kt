package org.avmedia.gshockGoogleSync.webhook

import AppSwitch
import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presets as chips: each fills in the message, type and triggers of a common target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetChips(onPick: (WebhookPreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WebhookPresets.ALL.forEach { p -> AssistChip(onClick = { onPick(p) }, label = { Text(p.label) }) }
    }
}

/** Where: name, address, method, headers. */
@Composable
internal fun WhereCard(
    name: String, onName: (String) -> Unit,
    url: String, onUrl: (String) -> Unit, urlHint: String?,
    method: String, onMethod: (String) -> Unit,
    headers: SnapshotStateList<WebhookHeader>,
) {
    HaCard("Where") {
        OutlinedTextField(
            value = name, onValueChange = onName, singleLine = true,
            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = url, onValueChange = onUrl,
            label = { Text("Address (https://...)") },
            placeholder = urlHint?.let { h -> { Text(h) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppText(text = "Method")
            Webhook.METHODS.forEach { m ->
                FilterChip(selected = method == m, onClick = { onMethod(m) }, label = { Text(m) })
            }
        }
        AppText(text = "Headers", fontSize = 14.sp)
        headers.forEachIndexed { index, header ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = header.key, onValueChange = { headers[index] = header.copy(key = it) },
                    singleLine = true, label = { Text("Name") }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = header.value, onValueChange = { headers[index] = header.copy(value = it) },
                    singleLine = true, label = { Text("Value") }, modifier = Modifier.weight(1.4f),
                )
                TextButton(onClick = { headers.removeAt(index) }) { Text("X") }
            }
        }
        TextButton(onClick = { headers.add(WebhookHeader()) }) { Text("Add header") }
    }
}

/** When: the triggers, the pause between sends, on or off. */
@Composable
internal fun WhenCard(
    onWatchAction: Boolean, onWatchActionChange: (Boolean) -> Unit,
    onSync: Boolean, onSyncChange: (Boolean) -> Unit,
    onHealth: Boolean, onHealthChange: (Boolean) -> Unit,
    onWorkout: Boolean, onWorkoutChange: (Boolean) -> Unit,
    interval: String, onInterval: (String) -> Unit,
    enabled: Boolean, onEnabled: (Boolean) -> Unit,
) {
    HaCard("When") {
        SwitchLine("A watch button runs the action", onWatchAction, onWatchActionChange)
        SwitchLine("After each watch connection", onSync, onSyncChange)
        SwitchLine("Health data downloaded", onHealth, onHealthChange)
        SwitchLine("Workout downloaded", onWorkout, onWorkoutChange)
        OutlinedTextField(
            value = interval, onValueChange = { onInterval(it.filter { c -> c.isDigit() }.take(5)) },
            singleLine = true, label = { Text("Pause between sends, minutes (0 = none)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchLine("On", enabled, onEnabled)
    }
}

@Composable
private fun SwitchLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(text = text, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** Inserts [text] into [body] at the cursor (replacing a selection). */
internal fun insertAtCursor(body: TextFieldValue, text: String): TextFieldValue {
    val sel = body.selection
    return TextFieldValue(body.text.replaceRange(sel.min, sel.max, text), TextRange(sel.min + text.length))
}
