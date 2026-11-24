package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.avmedia.gshockGoogleSync.plugins.FakePlugin
import org.avmedia.gshockGoogleSync.plugins.PluginDetails
import org.avmedia.gshockGoogleSync.plugins.PluginDetailsPage
import org.avmedia.gshockGoogleSync.plugins.PluginRow
import org.avmedia.gshockGoogleSync.plugins.PluginsContent
import org.avmedia.smartsync.homeassistant.HomeAssistantPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings > Plugins: the Plugins screen (on, off, failed, data of a removed plugin) and a plugin's details page. */
class PluginsScreenshotTest : ScreenshotTest() {
    private val ha = HomeAssistantPlugin()
    private val other = object : org.avmedia.smartsync.plugin.api.WatchPlugin by FakePlugin(id = "trmnl", name = "TRMNL") {
        override val icon: org.avmedia.smartsync.plugin.api.PluginIcon? = null
    }

    private val rows = listOf(
        PluginRow("home_assistant", ha, "Connected", on = true),
        PluginRow("trmnl", other, "Off", on = false),
        PluginRow("hc", FakePlugin(id = "hc", name = "Health Connect"), "Turned off: turned off after 5 failures in a row\nLast error: onEvent failed (IllegalStateException), 14:05 (5 failures)", on = false),
    )

    private val list = @androidx.compose.runtime.Composable { PluginsContent(rows, leftOver = setOf("webhook")) }

    private fun details(on: Boolean) = @androidx.compose.runtime.Composable {
        PluginDetailsPage(
            PluginRow("home_assistant", ha, if (on) "Connected" else "Off", on), worksWith = listOf("GBX-H5600 (all features)", "OCW-S7000 (without health log, steps)"),
            onSwitch = {}, onOpenScreen = {}, onDeleteData = {}, onBack = {},
        )
    }

    private fun openDetails() {
        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Reads").performClick()
    }

    @Test fun pluginsLight() = snap("settings_plugins", false, content = list)
    @Test fun pluginsDark() = snap("settings_plugins", true, content = list)
    @Test fun detailsLight() = snap("plugin_details", false, content = details(on = true))
    @Test fun detailsDark() = snap("plugin_details", true, content = details(on = true))
    @Test fun detailsOpenLight() = snap("plugin_details_open", false, before = ::openDetails, content = details(on = true))
    @Test fun detailsOpenDark() = snap("plugin_details_open", true, before = ::openDetails, content = details(on = true))
    @Test fun detailsOffLight() = snap("plugin_details_off", false, content = details(on = false))
    @Test fun detailsOffDark() = snap("plugin_details_off", true, content = details(on = false))

    // Owner 2026-11-24: what it does, whether it is on, what it needs and the guide; the rest behind "Details".
    @Test fun thePageReadsInSecondsAndTheRestIsOneTapAway() {
        composeRule.setContent { details(on = true)() }
        listOf(ha.description, "Connected", "Needs: ${ha.setupNeeds}", "Guide", "Details", "Delete its data").forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithText("Reads").assertDoesNotExist()
        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Reads").assertExists()
        composeRule.onNodeWithText(PluginDetails.EVERYTHING, substring = true).assertExists()
        composeRule.onNodeWithText(PluginDetails.ALL_CONTROLS).assertExists()
        composeRule.onNodeWithText("About this plugin").assertExists()
        composeRule.onNodeWithText("by ExcuseMi", substring = true).assertExists()
        // A tap on the row opens the section, a second tap folds it.
        composeRule.onNodeWithText("heart rate").assertDoesNotExist()
        composeRule.onNodeWithText("Reads").performClick()
        composeRule.onNodeWithText("heart rate").assertExists()
        composeRule.onNodeWithText("Reads").performClick()
        composeRule.onNodeWithText("heart rate").assertDoesNotExist()
        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Reads").assertDoesNotExist()
    }

    @Test fun theGuideLinkOpensTheInfoUrl() {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        composeRule.setContent { CompositionLocalProvider(LocalUriHandler provides handler) { details(on = true)() } }
        composeRule.onNodeWithText("Guide").performClick()
        assertEquals(listOf(ha.infoUrl), opened)
    }

    // The short-text rule: every line on the page is one short line, except the description (2 or 3 sentences).
    @Test fun everyLineIsShort() {
        composeRule.setContent { details(on = true)() }
        openDetails()
        val texts = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            .flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }
            .filter { it != ha.description }
        assertTrue(texts.isNotEmpty())
        val long = texts.filter { t -> t.lines().any { it.length > 80 || Regex("""[.!?] [A-Z]""").containsMatchIn(it) } }
        assertEquals("lines over the short-text rule", emptyList<String>(), long)
    }
}
