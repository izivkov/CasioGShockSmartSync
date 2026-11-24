package org.avmedia.smartsync.homeassistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** What a press runs when nothing is chosen: only the gshock_button event goes to Home Assistant. */
internal const val NOTHING = "Nothing (only the gshock_button event)"

/** The friendly name of the chosen action for the Actions row: its name only, never "script:" or the entity id. */
internal fun buttonActionName(chosen: HaAction?): String = chosen?.let { it.name.ifBlank { it.entityId } } ?: NOTHING

/** The actions whose name or entity id holds [query] (case ignored); all of them for a blank query. */
internal fun filterActions(actions: List<HaAction>, query: String): List<HaAction> {
    val q = query.trim()
    return if (q.isEmpty()) actions else actions.filter { it.name.contains(q, ignoreCase = true) || it.entityId.contains(q, ignoreCase = true) }
}

/** The full-screen picker for what a press of the watch button runs: search, Refresh, "Nothing" and every action. */
@Composable
internal fun HaActionPickerDialog(
    actions: HaActionsUi,
    onChoose: (HaAction?) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HaActionPickerContent(actions, { onChoose(it); onDismiss() }, onRefresh, onDismiss)
    }
}

/** The body of [HaActionPickerDialog] (also for the screenshot tests). State in, events out. */
@Composable
internal fun HaActionPickerContent(
    actions: HaActionsUi,
    onChoose: (HaAction?) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Watch button runs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !actions.loading) { Text("Refresh") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            when {
                actions.loading -> HaLoading("Loading your automations, scripts and scenes...")
                actions.error != null -> HaNote(actions.error, error = true)
                actions.items.isEmpty() -> HaNote("Home Assistant has no automations, scripts or scenes to pick.")
            }
            val shown = filterActions(actions.items, query)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (query.isBlank()) item { PickerRow(NOTHING, "", actions.chosen == null) { onChoose(null) } }
                items(shown, key = { it.entityId }) { a -> PickerRow(a.name.ifBlank { a.entityId }, a.kind, a == actions.chosen) { onChoose(a) } }
            }
        }
    }
}

@Composable
private fun PickerRow(name: String, kind: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (kind.isNotBlank()) AppText(kind, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Chosen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider()
}
