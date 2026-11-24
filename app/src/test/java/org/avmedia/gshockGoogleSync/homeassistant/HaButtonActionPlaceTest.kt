package org.avmedia.gshockGoogleSync.homeassistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The watch-button action is chosen only in the Actions tab's Home Assistant row (one line with the friendly name and
 * a picker with search); Settings > Home Assistant has no control for it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class HaButtonActionPlaceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val lights = HaAction("script", "script.lights", "Lights")
    private val movie = HaAction("scene", "scene.movie", "Movie night")
    private val configured = HaConfig("https://ha.example.com", "secret-token", "My G-Shock", "GBX-H5600")

    @Test fun theHomeAssistantScreenHasNoButtonActionControl() {
        rule.setContent { HaScreenContent(HaUi(form = configured, actions = HaActionsUi(items = listOf(lights), chosen = lights))) }
        rule.onNodeWithText("A press of the watch button runs").assertDoesNotExist()
        rule.onNodeWithText("Watch button", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Lights", substring = true).assertDoesNotExist()
    }

    @Test fun theActionsRowShowsTheFriendlyNameAndThePickerChoosesAnother() {
        var chosen: HaAction? = lights
        var refreshed = 0
        rule.setContent {
            HaButtonActionContent(true, true, {}, HaActionsUi(items = listOf(lights, movie), chosen = lights), { chosen = it }, { refreshed++ }, {})
        }
        rule.onNodeWithText("Watch button: Lights").assertIsDisplayed()
        rule.onNodeWithText("script:", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Refresh").assertDoesNotExist()
        rule.onNodeWithText("Watch button: Lights").performClick()
        rule.onNodeWithText("Search").assertExists()
        rule.onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshed)
        rule.onNodeWithText("Movie night").performClick()
        assertEquals(movie, chosen)
    }

    @Test fun notSetUpTheSecondLineOpensSettings() {
        var setup = 0
        rule.setContent { HaButtonActionContent(false, true, {}, HaActionsUi(), {}, {}, { setup++ }) }
        rule.onNodeWithText("Watch button", substring = true).assertDoesNotExist()
        rule.onNodeWithText(HA_SET_UP_LINK).performClick()
        assertEquals(1, setup)
    }

    @Test fun offTheRowIsOneLine() {
        rule.setContent { HaButtonActionContent(true, false, {}, HaActionsUi(items = listOf(lights), chosen = lights), {}, {}, {}) }
        rule.onNodeWithText("Home Assistant").assertIsDisplayed()
        rule.onNodeWithText("Watch button", substring = true).assertDoesNotExist()
    }
}
