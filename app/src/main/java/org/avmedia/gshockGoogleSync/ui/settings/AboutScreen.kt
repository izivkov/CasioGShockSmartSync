package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import AppTextLarge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.plugins.PluginsViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.HorizontalSeparator
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockGoogleSync.ui.watchhealth.ValueRow
import org.avmedia.gshockapi.io.TideStations
import org.avmedia.smartsync.plugin.api.PluginApi

/** One row of Settings that opens a screen: icon, title, a short summary on the right. */
@Composable
fun SettingsLinkRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            HorizontalSeparator(width = 8.dp)
            AppTextLarge(text = title, modifier = Modifier.weight(1f))
            AppText(text = "$summary  >", fontSize = 14.sp)
        }
    }
}

/** The bottom of Settings: the Plugins row ([pluginSummary], "2 on"), then About, last. */
@Composable
fun SettingsBottomRows(pluginSummary: String?, onOpenPlugins: () -> Unit, onOpenAbout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (pluginSummary != null) SettingsLinkRow(org.avmedia.gshockGoogleSync.plugins.FALLBACK_ICON, "Plugins", pluginSummary, onOpenPlugins)
        SettingsLinkRow(Icons.Filled.Info, "About", BuildConfig.VERSION_NAME, onOpenAbout)
    }
}

/** [SettingsBottomRows] with the plugins' count from the hub. */
@Composable
fun SettingsBottomLinks(onOpenPlugins: () -> Unit, onOpenAbout: () -> Unit, vm: PluginsViewModel = hiltViewModel()) {
    val states by vm.states.collectAsState()
    val count = vm.ids().size
    SettingsBottomRows(if (count == 0) null else org.avmedia.gshockGoogleSync.plugins.onSummary(states, count), onOpenPlugins, onOpenAbout)
}

/** The licences and credits About shows. */
internal val CREDITS: List<Pair<String, String>> = listOf(
    "Smart Sync" to "Apache License 2.0 (the LICENSE file of the app's repository).",
    "G-Shock library (GShockAPI)" to "MIT License.",
    "Home Assistant icon" to "Material Design Icons (Pictogrammers), Apache License 2.0.",
    "Tide data" to TideStations.ATTRIBUTION,
)

/** Settings > About: the versions, the licences and credits, and the App log (its screen has Share). */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenAppLog: () -> Unit) {
    BackHandler(onBack = onBack)
    AboutContent(BuildConfig.VERSION_NAME, BuildConfig.LIBRARY_VERSION, PluginApi.VERSION, onOpenAppLog)
}

@Composable
internal fun AboutContent(appVersion: String, libraryVersion: String, pluginApiVersion: String, onOpenAppLog: () -> Unit) {
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenTitle("About", Modifier)
                ValueRow(title = "App version", value = appVersion)
                ValueRow(title = "Library version", value = libraryVersion)
                ValueRow(title = "Plugin interface", value = pluginApiVersion)
                ValueRow(title = "App log", value = ">", onClick = onOpenAppLog)
                Text(
                    "Licences and credits", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        CREDITS.forEach { (title, text) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                AppText(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppText(text = text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
