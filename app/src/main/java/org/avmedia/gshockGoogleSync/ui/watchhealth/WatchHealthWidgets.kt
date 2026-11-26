package org.avmedia.gshockGoogleSync.ui.watchhealth

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppTimePicker

/** Text shown for a setting that is read but cannot be written yet. */
const val NOT_CONFIRMED = "not confirmed yet"

/** One settings row in the style of the other settings: a card with the title, the value, and an optional note. */
@Composable
fun ValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // A long value wraps within half the row: it never squeezes the title into a narrow column.
            val valueMax = maxWidth / 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f)
                    .then(if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppTextLarge(text = title)
                    if (note != null) AppText(text = note, fontSize = 12.sp)
                }
                AppTextLarge(
                    text = value,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.widthIn(max = valueMax).padding(start = 8.dp),
                )
            }
        }
    }
}

/** A settings row with a switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    note: String? = null,
    onChange: (Boolean) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppTextLarge(text = title)
                if (note != null) AppText(text = note, fontSize = 12.sp)
            }
            AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

/** Pick one of [options]; [onPick] gets its position. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, onClick = { onPick(index) })
                        Text(name)
                    }
                }
            }
        },
    )
}

/** Enter a whole number inside [range]; [onConfirm] only gets a valid one. */
@Composable
fun NumberDialog(
    title: String,
    initial: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in range }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(7) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("${range.first} to ${range.last}") },
                isError = value == null,
            )
        },
    )
}

/** Pick a time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            AppTimePicker(
                onConfirm = { state -> onConfirm(state.hour, state.minute) },
                onDismiss = onDismiss,
                initialHour = initialHour,
                initialMinute = initialMinute,
            )
        }
    }
}

fun hhmm(time: Pair<Int, Int>): String = "%02d:%02d".format(time.first, time.second)

/** Rows that open the Watch face, Watch health, Notification and Workout settings screens (DW-H5600 family only). */
@Composable
fun WatchHealthLinks(
    onOpenWatchFace: () -> Unit,
    onOpenWatchHealth: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenWorkouts: () -> Unit = {},
    onOpenTide: () -> Unit = {},
) {
    ValueRow(title = "Watch face", value = ">", onClick = onOpenWatchFace)
    ValueRow(title = "Tide", value = ">", onClick = onOpenTide)
    ValueRow(title = "Watch health settings", value = ">", onClick = onOpenWatchHealth)
    ValueRow(title = "Notification settings", value = ">", onClick = onOpenNotifications)
    ValueRow(title = "Activity settings", value = ">", onClick = onOpenWorkouts)
}
