package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.ui.test.onNodeWithText
import org.avmedia.gshockGoogleSync.plugins.FakePlugin
import org.avmedia.gshockGoogleSync.plugins.PluginDetailsPage
import org.avmedia.gshockGoogleSync.plugins.PluginRow
import org.avmedia.gshockGoogleSync.plugins.PluginsContent
import org.junit.Test

/** Settings > Plugins: the Plugins screen (on, off, failed, data of a removed plugin) and a plugin's details page. */
class PluginsScreenshotTest : ScreenshotTest() {
    private val ha = FakePlugin()
    private val other = object : org.avmedia.smartsync.plugin.api.WatchPlugin by FakePlugin(id = "trmnl", name = "TRMNL") {
        override val icon: org.avmedia.smartsync.plugin.api.PluginIcon? = null
    }

    private val rows = listOf(
        PluginRow("fake", ha, "Connected", on = true),
        PluginRow("trmnl", other, "Off", on = false),
        PluginRow("hc", FakePlugin(id = "hc", name = "Health Connect"), "Turned off: turned off after 5 failures in a row\nLast error: onEvent failed (IllegalStateException), 14:05 (5 failures)", on = false),
    )

    private val list = @androidx.compose.runtime.Composable { PluginsContent(rows, leftOver = setOf("webhook")) }

    private fun details(on: Boolean) = @androidx.compose.runtime.Composable {
        PluginDetailsPage(
            PluginRow("fake", ha, if (on) "Connected" else "Off", on), worksWith = listOf("GBX-H5600 (all features)", "OCW-S7000 (without health log, steps)"),
            onSwitch = {}, onOpenScreen = {}, onDeleteData = {}, onBack = {},
        )
    }

    @Test fun pluginsLight() = snap("settings_plugins", false, content = list)
    @Test fun pluginsDark() = snap("settings_plugins", true, content = list)
    @Test fun detailsLight() = snap("plugin_details", false, content = details(on = true))
    @Test fun detailsDark() = snap("plugin_details", true, content = details(on = true))
    @Test fun detailsOffLight() = snap("plugin_details_off", false, content = details(on = false))
    @Test fun detailsOffDark() = snap("plugin_details_off", true, content = details(on = false))

    @Test fun theDetailsListTheDeclarationInPlainWords() {
        composeRule.setContent { details(on = true)() }
        listOf("Reads", "steps, distance and energy, heart rate, sleep", "Controls", "show notifications on the watch, change watch settings",
            "Sends to", "your Home Assistant server", "Keeps", "Home Assistant address and token", "Works with", "GBX-H5600 (all features)",
            "Each watch as a Home Assistant device.", "Delete its data", "Turn it off to delete its data.",
        ).forEach { composeRule.onNodeWithText(it).assertExists() }
    }
}
