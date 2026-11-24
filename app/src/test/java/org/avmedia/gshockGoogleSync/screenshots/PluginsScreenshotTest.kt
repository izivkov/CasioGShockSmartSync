package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.plugins.FakePlugin
import org.avmedia.gshockGoogleSync.plugins.PluginCard
import org.avmedia.gshockGoogleSync.plugins.PluginDetailsContent
import org.junit.Test

/** Settings > Plugins: the plugin rows (on, off, failed) and a plugin's details with what it uses. */
class PluginsScreenshotTest : ScreenshotTest() {
    private val ha = FakePlugin()
    private val other = object : org.avmedia.smartsync.plugin.api.WatchPlugin by FakePlugin(id = "trmnl", name = "TRMNL") {
        override val icon: org.avmedia.smartsync.plugin.api.PluginIcon? = null
    }

    private fun list() = @androidx.compose.runtime.Composable {
        Column {
            PluginCard(ha, "Connected", on = true, onSwitch = {}, onOpen = {})
            PluginCard(other, "Off", on = false, onSwitch = {}, onOpen = {})
            PluginCard(FakePlugin(id = "hc", name = "Health Connect"), "Turned off: turned off after 5 failures in a row\nLast error: onEvent failed (IllegalStateException), 14:05 (5 failures)", on = false, onSwitch = {}, onOpen = {})
        }
    }

    private val details = @androidx.compose.runtime.Composable {
        Column(Modifier.padding(16.dp)) {
            PluginDetailsContent(ha, on = true, status = "Connected", worksWith = listOf("GBX-H5600 (all features)", "OCW-S7000 (without health log, steps)"), onSwitch = {})
        }
    }

    @Test fun pluginsLight() = snap("settings_plugins", false, content = list())
    @Test fun pluginsDark() = snap("settings_plugins", true, content = list())
    @Test fun detailsLight() = snap("plugin_details", false, content = details)
    @Test fun detailsDark() = snap("plugin_details", true, content = details)

    @Test fun theDetailsListTheDeclarationInPlainWords() {
        composeRule.setContent { details() }
        listOf("Reads", "steps, distance and energy, heart rate, sleep", "Controls", "show notifications on the watch, change watch settings",
            "Sends to", "your Home Assistant server", "Keeps", "Home Assistant address and token", "Works with", "GBX-H5600 (all features)",
        ).forEach { composeRule.onNodeWithText(it).assertExists() }
    }
}
