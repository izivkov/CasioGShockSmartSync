package org.avmedia.gshockGoogleSync.plugins

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.HorizontalSeparator
import org.avmedia.smartsync.plugin.api.PluginIcon
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** The plugins for Settings > Plugins, their Actions cards and their screens. */
@HiltViewModel
class PluginsViewModel @Inject constructor(private val hub: PluginHub) : ViewModel() {
    val states = hub.runtime.states
    val health = hub.health.health
    fun plugins(): List<WatchPlugin> = hub.runtime.plugins()
    fun setEnabled(id: String, on: Boolean) = hub.setEnabled(id, on)
    fun accessShown(id: String) = hub.accessShown(id)
    fun markAccessShown(id: String) = hub.markAccessShown(id)
    fun worksWith(p: WatchPlugin): List<String> =
        AccessGuard.worksWith(hub.supportOf(p).map { (w, s) -> w.name.removePrefix("CASIO").trim().ifBlank { w.id.key } to s })
}

/** What a plugin's row shows under its name when it is not running. Null: the plugin's own status line. */
internal fun offLine(state: PluginState?): String? = when (state) {
    null, PluginState.Off -> "Off"
    PluginState.Active -> null
    is PluginState.Failed -> "Turned off: ${state.reason}"
    is PluginState.Incompatible -> "Off: this app needs a newer plugin"
}

/** A plugin's icon, 28 dp in the app's accent; the host's plugin icon when it gives none. */
@Composable
internal fun PluginIconView(icon: PluginIcon?, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    val m = modifier.size(28.dp)
    when (val shown = iconOf(icon)) {
        is PluginIcon.Drawable -> Icon(painterResource(shown.id), contentDescription = null, tint = tint, modifier = m)
        is PluginIcon.Vector -> Icon(shown.image, contentDescription = null, tint = tint, modifier = m)
    }
}

/** The icon shown for a plugin: its own, else [FALLBACK_ICON]. */
internal fun iconOf(icon: PluginIcon?): PluginIcon = icon ?: PluginIcon.Vector(FALLBACK_ICON)

/** The icon of a plugin that has none. */
internal val FALLBACK_ICON = Icons.Filled.Extension

/**
 * Settings > Plugins: every plugin found, with its icon, name, version, status and switch. A tap opens its details
 * (what it uses, its screen); switching one on for the first time shows what it uses before it starts.
 */
@Composable
fun PluginSettingsEntries(onOpen: (String) -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val states by vm.states.collectAsState()
    val health by vm.health.collectAsState()
    val plugins = vm.plugins()
    var details by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    val switch = { id: String, on: Boolean -> if (on && !vm.accessShown(id)) asking = id else vm.setEnabled(id, on) }
    if (plugins.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Plugins")
        for (p in plugins) {
            val state = states[p.id]
            PluginCard(p, statusOf(p, state, health[p.id]), on = state == PluginState.Active, onSwitch = { switch(p.id, it) }, onOpen = { details = p.id })
        }
    }
    plugins.firstOrNull { it.id == details }?.let { p ->
        PluginDetails(
            plugin = p, on = states[p.id] == PluginState.Active, status = statusOf(p, states[p.id], health[p.id]), worksWith = vm.worksWith(p),
            onSwitch = { switch(p.id, it) }, onOpenScreen = if (p.ui != null) ({ details = null; onOpen(p.id) }) else null,
            onDismiss = { details = null },
        )
    }
    plugins.firstOrNull { it.id == asking }?.let { p ->
        FirstOnDialog(p, onTurnOn = { vm.markAccessShown(p.id); vm.setEnabled(p.id, true); asking = null }, onCancel = { asking = null })
    }
}

@Composable
private fun statusOf(p: WatchPlugin, state: PluginState?, health: PluginHealth?): String {
    val base = offLine(state) ?: (p.ui?.statusLine() ?: "On")
    val error = health?.let { h -> PluginHealthBoard.line(h) { ms -> shortTime(ms) } }
    return listOfNotNull(base, error).joinToString("\n")
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** One card per plugin with an Actions row: icon, name, the same switch, and the plugin's own line while it is on. */
@Composable
fun PluginActionRows(onOpenSettings: (String) -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val states by vm.states.collectAsState()
    for (p in vm.plugins()) {
        if (p.ui?.hasActionsRow != true) continue
        PluginActionCard(p, on = states[p.id] == PluginState.Active, onSwitch = { vm.setEnabled(p.id, it) }, onOpenSettings = { onOpenSettings(p.id) })
    }
}

@Composable
internal fun PluginActionCard(plugin: WatchPlugin, on: Boolean, onSwitch: (Boolean) -> Unit, onOpenSettings: () -> Unit) {
    val ui = plugin.ui ?: return
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PluginIconView(plugin.icon)
                HorizontalSeparator(width = 4.dp)
                AppTextLarge(text = plugin.name, modifier = Modifier.weight(1f))
                AppSwitch(checked = on, onCheckedChange = onSwitch)
            }
            if (on) ui.ActionsRow(onOpenSettings = onOpenSettings)
        }
    }
}

@Composable
internal fun PluginCard(plugin: WatchPlugin, status: String, on: Boolean, onSwitch: (Boolean) -> Unit, onOpen: (() -> Unit)?) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().let { m -> onOpen?.let { m.clickable(onClick = it) } ?: m }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginIconView(plugin.icon)
            HorizontalSeparator(width = 8.dp)
            Column(modifier = Modifier.weight(1f)) {
                AppTextLarge(text = plugin.name)
                AppText(text = listOf(versionOf(plugin), status).filter { it.isNotBlank() }.joinToString("  ·  "), fontSize = 12.sp)
            }
            AppSwitch(checked = on, onCheckedChange = onSwitch)
        }
    }
}

private fun versionOf(p: WatchPlugin): String = runCatching { p.version }.getOrDefault("").let { if (it.isBlank()) "" else "v$it" }

/** What a plugin reads, controls, sends where and keeps, in plain words. */
@Composable
internal fun AccessLines(plugin: WatchPlugin) {
    val access = runCatching { plugin.access }.getOrNull() ?: org.avmedia.smartsync.plugin.api.PluginAccess()
    for ((title, text) in AccessGuard.lines(access)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            AppText(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppText(text = text, fontSize = 14.sp)
        }
    }
}

/** A plugin's details: icon, name, version, status, the switch, what it uses, and its screen. */
@Composable
internal fun PluginDetails(
    plugin: WatchPlugin, on: Boolean, status: String, onSwitch: (Boolean) -> Unit, onOpenScreen: (() -> Unit)?, onDismiss: () -> Unit,
    worksWith: List<String> = emptyList(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PluginIconView(plugin.icon) },
        title = { Text(plugin.name) },
        text = { PluginDetailsContent(plugin, on, status, worksWith, onSwitch) },
        confirmButton = { if (onOpenScreen != null) TextButton(onClick = onOpenScreen) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** The body of the details: version and status with the switch, what the plugin uses, the watches it works with. */
@Composable
internal fun PluginDetailsContent(plugin: WatchPlugin, on: Boolean, status: String, worksWith: List<String>, onSwitch: (Boolean) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(text = listOf(versionOf(plugin), status).filter { it.isNotBlank() }.joinToString("  ·  "), fontSize = 12.sp, modifier = Modifier.weight(1f))
            AppSwitch(checked = on, onCheckedChange = onSwitch)
        }
        AccessLines(plugin)
        if (worksWith.isNotEmpty()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AppText(text = "Works with", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                worksWith.forEach { AppText(text = it, fontSize = 14.sp) }
            }
        }
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

/** The open plugin's own screen. */
@Composable
fun PluginScreen(id: String, onBack: () -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val ui = vm.plugins().firstOrNull { it.id == id }?.ui
    if (ui == null) androidx.compose.runtime.LaunchedEffect(id) { onBack() } else ui.SettingsScreen(onBack)
}

/** "14:05" today, else "24 Nov 14:05". */
internal fun shortTime(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    val pattern = if (t.toLocalDate() == java.time.LocalDate.now()) "HH:mm" else "d MMM HH:mm"
    return java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(t)
}
