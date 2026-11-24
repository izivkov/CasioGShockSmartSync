package org.avmedia.smartsync.homeassistant

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The Home Assistant status line and its Actions line: only the button action, and the way to the setup. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HaEntriesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theStatusSaysSetUpOrNotAndAFailedSend() {
        assertEquals("Connected", HaStatus.text(saved = true, lastOk = null))
        assertEquals("Not set up", HaStatus.text(saved = false, lastOk = true))
        assertEquals("Last send failed", HaStatus.text(saved = true, lastOk = false))
    }

    @Test fun theActionsLineOnlyChoosesTheButtonAction() {
        val actions = HaActionsUi(items = listOf(HaAction("script", "script.lights", "Lights")))
        rule.setContent { HaButtonActionContent(true, actions, {}, {}, {}) }
        rule.onNodeWithText("Watch button:", substring = true).assertExists()
        listOf("Token", "Address", "Forget", "Dashboard", "Find").forEach {
            rule.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }

    @Test fun theActionsLineLinksToTheSetupWhenNotSetUp() {
        var setup = 0
        rule.setContent { HaButtonActionContent(false, HaActionsUi(), {}, {}, { setup++ }) }
        rule.onNodeWithText("Watch button:", substring = true).assertDoesNotExist()
        rule.onNodeWithText(HA_SET_UP_LINK).performClick()
        assertEquals(1, setup)
    }
}
