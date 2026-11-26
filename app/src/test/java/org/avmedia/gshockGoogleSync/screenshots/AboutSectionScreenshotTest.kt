package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onNodeWithText
import org.avmedia.gshockGoogleSync.ui.settings.AboutContent
import org.avmedia.gshockGoogleSync.ui.settings.SettingsBottomRows
import org.junit.Test

/** The bottom of Settings (one Plugins row, then About, last) and the About screen with versions, credits and the App log. */
class AboutSectionScreenshotTest : ScreenshotTest() {
    private val rows = @androidx.compose.runtime.Composable { Column { SettingsBottomRows("2 on", onOpenPlugins = {}, onOpenAbout = {}) } }
    private val about = @androidx.compose.runtime.Composable { AboutContent("42.9.30", "1.4.2", "0.1.0", onOpenAppLog = {}) }

    @Test fun rowsLight() = snap("settings_bottom_rows", false, content = rows)
    @Test fun rowsDark() = snap("settings_bottom_rows", true, content = rows)
    @Test fun aboutLight() = snap("settings_about", false, content = about)
    @Test fun aboutDark() = snap("settings_about", true, content = about)

    @Test fun aboutShowsTheThreeVersionsTheCreditsAndTheLog() {
        composeRule.setContent { about() }
        listOf("App version", "42.9.30", "Library version", "1.4.2", "Plugin interface", "0.1.0", "App log", "Licences and credits", "Tide data")
            .forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithText("TICON-4", substring = true).assertExists()
    }

    @Test fun theRowsArePluginsThenAboutLast() {
        composeRule.setContent { rows() }
        composeRule.onNodeWithText("Plugins").assertExists()
        composeRule.onNodeWithText("2 on  >").assertExists()
        val plugins = composeRule.onNodeWithText("Plugins").fetchSemanticsNode().boundsInRoot.top
        val about = composeRule.onNodeWithText("About").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("About is below Plugins", about > plugins)
    }
}
