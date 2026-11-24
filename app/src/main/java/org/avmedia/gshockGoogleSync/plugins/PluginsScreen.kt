package org.avmedia.gshockGoogleSync.plugins

import AppSwitch
import AppText
import AppTextLarge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.HorizontalSeparator
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** One row of the Plugins screen. */
internal data class PluginRow(val id: String, val plugin: WatchPlugin, val status: String, val on: Boolean, val needsPermission: Boolean = false)

/**
 * Settings > Plugins: every plugin found (icon, name, version, status, switch). A tap opens its details page (what it
 * uses, its screen, deleting its data); switching one on for the first time shows what it uses before it starts. A
 * plugin that asks for it gets its screen each time it is on again ([OpenOnTurnOn]).
 */
@Composable
fun PluginsScreen(onBack: () -> Unit, onOpenPlugin: (String) -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val states by vm.states.collectAsState()
    val health by vm.health.collectAsState()
    var details by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    var dataChanged by remember { mutableIntStateOf(0) }
    val opener = remember { OpenOnTurnOn() }
    LaunchedEffect(states) { opener.take(states)?.let(onOpenPlugin) }
    val turnOn = { id: String ->
        opener.turnedOn(id, vm.plugin(id)?.ui?.opensOnTurnOn == true)
        vm.setEnabled(id, true)
    }
    val switch = { id: String, on: Boolean -> if (on && !vm.accessShown(id)) asking = id else if (on) turnOn(id) else vm.setEnabled(id, false) }
    val rows = vm.ids().mapNotNull { id ->
        vm.plugin(id)?.let { PluginRow(id, it, statusText(states[id], health[id], vm.missingPermissions(id)), states[id] == PluginState.Active, vm.missingPermissions(id).isNotEmpty()) }
    }
    val leftOver = remember(dataChanged) { vm.leftOver() }

    val open = details?.let { id -> rows.firstOrNull { it.id == id } }
    if (open != null) {
        BackHandler { details = null }
        PluginDetailsPage(
            row = open, worksWith = vm.worksWith(open.plugin),
            onSwitch = { switch(open.id, it) },
            onOpenScreen = if (open.plugin.ui != null) ({ onOpenPlugin(open.id) }) else null,
            onDeleteData = { vm.clearData(open.id); dataChanged++ },
            onBack = { details = null },
            onGrant = { vm.requestPermissions(open.id) },
        )
    } else {
        BackHandler(onBack = onBack)
        PluginsContent(rows, leftOver, onSwitch = switch, onOpen = { details = it }, onDeleteLeftOver = { vm.clearData(it); dataChanged++ })
    }
    asking?.let { id ->
        vm.plugin(id)?.let { p -> FirstOnDialog(p, onTurnOn = { vm.markAccessShown(id); asking = null; turnOn(id) }, onCancel = { asking = null }) }
    }
}

/** The list itself (also for screenshots). */
@Composable
internal fun PluginsContent(
    rows: List<PluginRow>,
    leftOver: Set<String> = emptySet(),
    onSwitch: (String, Boolean) -> Unit = { _, _ -> },
    onOpen: (String) -> Unit = {},
    onDeleteLeftOver: (String) -> Unit = {},
) {
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenTitle("Plugins", Modifier)
                if (rows.isEmpty()) AppText(text = "No plugins in this app.", modifier = Modifier.padding(16.dp))
                rows.forEach { r -> PluginCard(r.plugin, r.status, r.on, onSwitch = { onSwitch(r.id, it) }, onOpen = { onOpen(r.id) }) }
                leftOver.sorted().forEach { id ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppText(text = "Data of $id, a plugin no longer in this app", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onDeleteLeftOver(id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

/** A plugin's details page: icon, name, summary, version and status with the switch, what it uses, its screen, its data. */
@Composable
internal fun PluginDetailsPage(
    row: PluginRow,
    worksWith: List<String>,
    onSwitch: (Boolean) -> Unit,
    onOpenScreen: (() -> Unit)?,
    onDeleteData: () -> Unit,
    onBack: () -> Unit,
    onGrant: () -> Unit = {},
) {
    var confirm by remember { mutableStateOf(false) }
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenTitle(row.plugin.name, Modifier)
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PluginIconView(row.plugin.icon)
                            HorizontalSeparator(width = 8.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                AppTextLarge(text = row.plugin.name)
                                AppText(text = listOf(versionOf(row.plugin), row.status).filter { it.isNotBlank() }.joinToString("  ·  "), fontSize = 12.sp)
                            }
                            AppSwitch(checked = row.on, onCheckedChange = onSwitch)
                        }
                        runCatching { row.plugin.summary }.getOrDefault("").takeIf { it.isNotBlank() }?.let {
                            AppText(text = it, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        AccessLines(row.plugin)
                        if (worksWith.isNotEmpty()) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                AppText(text = "Works with", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                worksWith.forEach { AppText(text = it, fontSize = 14.sp) }
                            }
                        }
                    }
                }
                if (row.on && row.needsPermission) {
                    OutlinedButton(onClick = onGrant, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Grant permissions") }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (onOpenScreen != null) OutlinedButton(onClick = onOpenScreen, modifier = Modifier.padding(end = 8.dp)) { Text("Open") }
                    // Only while off: a running plugin would write its data again at once.
                    OutlinedButton(onClick = { confirm = true }, enabled = !row.on) { Text("Delete its data") }
                }
                if (row.on) AppText(text = "Turn it off to delete its data.", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Back") }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${row.plugin.name}'s data?") },
            text = { Text("Its settings, tokens and what it kept per watch are deleted from this phone.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDeleteData() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Before a plugin is turned on the first time: what it uses. */
@Composable
internal fun FirstOnDialog(plugin: WatchPlugin, onTurnOn: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { PluginIconView(plugin.icon) },
        title = { Text("Turn on ${plugin.name}?") },
        text = { Column { AccessLines(plugin) } },
        confirmButton = { TextButton(onClick = onTurnOn) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
