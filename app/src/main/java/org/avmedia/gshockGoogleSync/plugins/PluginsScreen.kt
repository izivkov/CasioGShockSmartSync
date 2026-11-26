package org.avmedia.gshockGoogleSync.plugins

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.HorizontalSeparator
import org.avmedia.gshockGoogleSync.ui.common.AppSubScreen
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** One row of the Plugins screen. */
internal data class PluginRow(val id: String, val plugin: WatchPlugin, val status: String, val on: Boolean, val needsPermission: Boolean = false)

/**
 * Settings > Plugins: every plugin found (icon, name, version, status, switch). A tap opens its details page (what it
 * does, its switch and guide; the rest behind "Details"); switching one on for the first time shows what it uses before
 * it starts. A plugin that asks for it gets its screen each time it is turned on (the hub opens it, from any switch).
 */
@Composable
fun PluginsScreen(onBack: () -> Unit, onOpenPlugin: (String) -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val states by vm.states.collectAsState()
    val health by vm.health.collectAsState()
    val missing by vm.permissions.collectAsState()
    var details by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    var dataChanged by remember { mutableIntStateOf(0) }
    val switch = { id: String, on: Boolean -> if (on && !vm.accessShown(id)) asking = id else vm.setEnabled(id, on) }
    val rows = vm.ids().mapNotNull { id ->
        val m = missing[id].orEmpty()
        vm.plugin(id)?.let { PluginRow(id, it, statusText(states[id], health[id], m), states[id] == PluginState.Active, m.isNotEmpty()) }
    }
    val leftOver = remember(dataChanged) { vm.leftOver() }

    val open = details?.let { id -> rows.firstOrNull { it.id == id } }
    if (open != null) {
        PluginDetailsPage(
            row = open, worksWith = vm.worksWith(open.plugin),
            onSwitch = { switch(open.id, it) },
            onOpenScreen = if (open.plugin.ui != null) ({ onOpenPlugin(open.id) }) else null,
            onDeleteData = { vm.clearData(open.id); dataChanged++ },
            onBack = { details = null },
            onGrant = { vm.requestPermissions(open.id) },
        )
    } else {
        PluginsContent(rows, leftOver, onSwitch = switch, onOpen = { details = it }, onDeleteLeftOver = { vm.clearData(it); dataChanged++ }, onGrant = vm::requestPermissions, onBack = onBack)
    }
    asking?.let { id ->
        vm.plugin(id)?.let { p -> FirstOnDialog(p, onTurnOn = { vm.markAccessShown(id); asking = null; vm.setEnabled(id, true) }, onCancel = { asking = null }) }
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
    onGrant: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    AppSubScreen(title = "Plugins", onBack = onBack) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (rows.isEmpty()) AppText(text = "No plugins in this app.", modifier = Modifier.padding(16.dp))
            rows.forEach { r ->
                PluginCard(
                    r.plugin, r.status, r.on, onSwitch = { onSwitch(r.id, it) }, onOpen = { onOpen(r.id) },
                    onStatus = if (r.on && r.needsPermission) ({ onGrant(r.id) }) else null,
                )
            }
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

/**
 * A plugin's details page, readable in seconds: what it does (its description), its switch and status, what it needs
 * and its guide. Everything about data and permissions sits behind one "Details" row ([PluginDetailSections]).
 */
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
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    AppSubScreen(title = row.plugin.name, onBack = onBack) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PluginIconView(row.plugin.icon)
                        HorizontalSeparator(width = 8.dp)
                        AppTextLarge(text = row.plugin.name, modifier = Modifier.weight(1f))
                        AppText(text = versionOf(row.plugin), fontSize = 12.sp)
                    }
                    PluginDetails.description(row.plugin).takeIf { it.isNotBlank() }?.let {
                        AppText(text = it, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText(text = row.status, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        AppSwitch(checked = row.on, onCheckedChange = onSwitch)
                    }
                    PluginDetails.needs(row.plugin)?.let { AppText(text = it, fontSize = 14.sp) }
                    PluginDetails.guide(row.plugin)?.let { url ->
                        TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Guide") }
                    }
                }
            }
            if (row.on && row.needsPermission) {
                OutlinedButton(onClick = onGrant, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Grant permissions") }
            }
            AppCard(modifier = Modifier.fillMaxWidth()) { PluginDetailSections(PluginDetails.sections(row.plugin, worksWith)) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (onOpenScreen != null) OutlinedButton(onClick = onOpenScreen, modifier = Modifier.padding(end = 8.dp)) { Text("Open") }
                // Only while off: a running plugin would write its data again at once.
                OutlinedButton(onClick = { confirm = true }, enabled = !row.on) { Text("Delete its data") }
            }
            if (row.on) AppText(text = "Turn it off to delete its data.", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${row.plugin.name}'s data?") },
            text = { Text("Its settings, tokens and per-watch data go.") },
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
