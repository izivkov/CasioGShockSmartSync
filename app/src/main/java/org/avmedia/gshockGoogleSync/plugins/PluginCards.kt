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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginIcon
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** The plugins for Settings > Plugins, their Actions cards and their screens. */
@HiltViewModel
class PluginsViewModel @Inject constructor(private val hub: PluginHub) : ViewModel() {
    val states = hub.runtime.states
    val health = hub.health.health
    fun plugins(): List<WatchPlugin> = hub.runtime.ids().mapNotNull { hub.runtime.plugin(it) }
    fun ids(): List<String> = hub.runtime.ids()
    fun plugin(id: String): WatchPlugin? = hub.runtime.plugin(id)
    fun setEnabled(id: String, on: Boolean) = hub.setEnabled(id, on)
    fun accessShown(id: String) = hub.accessShown(id)
    fun markAccessShown(id: String) = hub.markAccessShown(id)
    fun watchRemoved(address: String) = hub.watchRemoved(address)
    fun clearData(id: String) = hub.clearData(id)
    fun leftOver(): Set<String> = hub.leftOverPlugins()
    fun missingPermissions(id: String) = hub.missingPermissions(id)
    fun requestPermissions(id: String) = hub.requestPermissions(id)

    /** "Home Assistant (all features)" for each plugin that is offered for the watch at [address]. */
    fun pluginsFor(address: String): List<String> {
        val id = org.avmedia.smartsync.plugin.api.WatchId(org.avmedia.gshockGoogleSync.watch.WatchKeys.of(address))
        val caps = hub.capsOf(id)
        return plugins().mapNotNull { p ->
            val s = WatchSupport.of(runCatching { p.access }.getOrDefault(PluginAccess()), caps)
            if (s is WatchSupport.NotSupported) null else AccessGuard.worksWith(listOf(p.name to s)).single()
        }
    }

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

/**
 * The status line of a plugin: off, else the text of its last [org.avmedia.smartsync.plugin.api.PluginStatus.Ok]; then
 * "Needs permission: ..." while one is missing ([missing], [PluginPermissions.line]); then its last error.
 */
internal fun statusText(
    state: PluginState?, health: PluginHealth?, missing: List<org.avmedia.smartsync.plugin.api.AndroidPermission> = emptyList(),
    time: (Long) -> String = ::shortTime,
): String {
    val base = offLine(state) ?: health?.okText?.takeIf { it.isNotBlank() } ?: "On"
    val needs = PluginPermissions.line(missing)?.takeIf { state == PluginState.Active }
    val error = health?.let { h -> PluginHealthBoard.line(h, time) }
    return listOfNotNull(base, needs, error).joinToString("\n")
}

/** "2 on", "none on", "1 on of 3" for the Plugins row of Settings. */
internal fun onSummary(states: Map<String, PluginState>, count: Int): String {
    if (count == 0) return "none found"
    val on = states.values.count { it == PluginState.Active }
    return "$on on"
}

/**
 * Opens a plugin's screen once it is active after the person turned it on (every turn-on, not only the first), for a
 * plugin that asks for that ([org.avmedia.smartsync.plugin.api.PluginUi.opensOnTurnOn]). Android-free.
 */
class OpenOnTurnOn {
    private var waiting: String? = null

    fun turnedOn(id: String, opens: Boolean) { if (opens) waiting = id }

    /** The id to open now, once: when [states] show it active. A plugin that failed to start opens nothing. */
    fun take(states: Map<String, PluginState>): String? {
        val id = waiting ?: return null
        return when (states[id]) {
            PluginState.Active -> { waiting = null; id }
            is PluginState.Failed, is PluginState.Incompatible -> { waiting = null; null }
            else -> null
        }
    }
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

/** The icon of a plugin that has none, and of the Plugins row in Settings. */
internal val FALLBACK_ICON = Icons.Filled.Extension

/** One card per plugin with an Actions row: icon, name, the same switch, and the plugin's own line while it is on. */
@Composable
fun PluginActionRows(onOpenSettings: (String) -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val states by vm.states.collectAsState()
    for (id in vm.ids()) {
        val p = vm.plugin(id) ?: continue
        if (p.ui?.hasActionsRow != true) continue
        PluginActionCard(p, on = states[id] == PluginState.Active, onSwitch = { vm.setEnabled(id, it) }, onOpenSettings = { onOpenSettings(id) })
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

/** One plugin in the Plugins screen: icon, name, version and status, its switch; a tap opens its details page. */
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

internal fun versionOf(p: WatchPlugin): String = runCatching { p.version }.getOrDefault("").let { if (it.isBlank()) "" else "v$it" }

/** What a plugin reads, controls, sends where and keeps, in plain words. */
@Composable
internal fun AccessLines(plugin: WatchPlugin) {
    val access = runCatching { plugin.access }.getOrNull() ?: PluginAccess()
    for ((title, text) in AccessGuard.lines(access)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            AppText(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppText(text = text, fontSize = 14.sp)
        }
    }
}

/** The open plugin's own screen. */
@Composable
fun PluginScreen(id: String, onBack: () -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val ui = vm.plugin(id)?.ui
    if (ui == null) androidx.compose.runtime.LaunchedEffect(id) { onBack() } else ui.SettingsScreen(onBack)
}

/** "14:05" today, else "24 Nov 14:05". */
internal fun shortTime(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    val pattern = if (t.toLocalDate() == java.time.LocalDate.now()) "HH:mm" else "d MMM HH:mm"
    return java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(t)
}

/** The ⋮ sheet of a watch: the plugins offered for it and how far each works with it. */
@Composable
fun PluginsForWatch(address: String, vm: PluginsViewModel = hiltViewModel()) {
    val lines = remember(address) { vm.pluginsFor(address) }
    if (lines.isEmpty()) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        AppText(text = "Plugins for this watch", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { AppText(text = it, fontSize = 14.sp) }
    }
}
