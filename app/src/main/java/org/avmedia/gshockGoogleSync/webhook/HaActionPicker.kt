package org.avmedia.gshockGoogleSync.webhook

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val NOTHING = "Nothing (only the gshock_button event)"

/** "Watch button runs": what a Phone Finder press starts in HA besides the gshock_button event. State in, events out. */
@Composable
internal fun HaActionPicker(
    actions: List<HaAction>,
    chosen: HaAction?,
    status: String,
    onChoose: (HaAction?) -> Unit,
    onRefresh: () -> Unit,
    initiallyOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    AppText(text = "Watch button runs", fontSize = 14.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(chosen?.let { "${it.kind}: ${it.name}" } ?: NOTHING)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(NOTHING) }, onClick = { open = false; onChoose(null) })
                actions.forEach { a ->
                    DropdownMenuItem(text = { Text("${a.kind}: ${a.name}") }, onClick = { open = false; onChoose(a) })
                }
            }
        }
        TextButton(onClick = onRefresh) { Text("Refresh") }
    }
    if (status.isNotBlank()) AppText(text = status, fontSize = 14.sp)
}
