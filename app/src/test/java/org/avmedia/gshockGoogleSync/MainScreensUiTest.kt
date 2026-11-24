package org.avmedia.gshockGoogleSync

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.session.LinkState
import org.avmedia.gshockGoogleSync.ui.time.TimeTitleRow
import org.avmedia.gshockGoogleSync.ui.watchhealth.ValueRow
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchFaceContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchFaceUi
import org.avmedia.gshockapi.io.WatchFaceCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The way to the watch list, the link bar, Home Assistant's entries and the removed decoding aids. */
@RunWith(RobolectricTestRunner::class)
// Real text measuring (as in the screenshot tests): the layout checks need true text widths.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class MainScreensUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun backOnTheTimeTabOpensTheWatchList() {
        var opened = 0
        rule.setContent { TimeTabBack(Screens.Time.route) { opened++ } }
        pressBack()
        assertEquals(1, opened)
    }

    @Test fun backOnAnotherTabStillGoesToTime() {
        var opened = 0
        var toTime = 0
        rule.setContent {
            BackHandler { toTime++ } // stands for the NavHost popping back to Time
            TimeTabBack(Screens.Settings.route) { opened++ }
        }
        pressBack()
        assertEquals("the watch list does not open", 0, opened)
        assertEquals(1, toTime)
    }

    @Test fun theTimeTabHasAWatchesButtonTopLeft() {
        var opened = 0
        rule.setContent { TimeTitleRow { opened++ } }
        val button = rule.onNodeWithContentDescription("Watches")
        button.assertIsDisplayed()
        rule.onNodeWithText("Time").assertIsDisplayed()
        assertTrue("the button sits top left", button.getBoundsInRoot().left < 24.dp)
        button.performClick()
        assertEquals(1, opened)
    }

    @Test fun theLinkBarAndTheTitleBothShow() {
        rule.setContent { LinkBarLayout(LinkState.RECONNECTING) { Text("Time") } }
        val bar = rule.onNodeWithText("Reconnecting...")
        val title = rule.onNodeWithText("Time")
        bar.assertIsDisplayed()
        title.assertIsDisplayed()
        assertTrue(
            "the bar sits above the title, not over it",
            bar.getBoundsInRoot().bottom <= title.getBoundsInRoot().top,
        )
    }

    @Test fun theWatchFaceScreenHasNoDetailsControl() {
        val ui = WatchFaceUi(face = WatchFaceCodec.Face(2, listOf(0, 10, 12, 19)))
        rule.setContent { WatchFaceContent(ui, {}, {}, {}) }
        rule.onNodeWithText("Face").assertExists()
        rule.onNodeWithText("Details").assertDoesNotExist()
        rule.onNodeWithText("Hide details").assertDoesNotExist()
        rule.onNodeWithText("For decoding only", substring = true).assertDoesNotExist()
    }

    @Test fun aLongValueDoesNotSqueezeTheTitle() {
        rule.setContent {
            Column(Modifier.width(360.dp)) {
                ValueRow("Target zone", ">", false, note = "Read only")
                ValueRow("Target zone", "Zone 2, 120 to 150 bpm and a long text that needs several lines", false, note = "Read only")
            }
        }
        val titles = rule.onAllNodesWithText("Target zone")
        val alone = titles[0].getBoundsInRoot()
        val long = titles[1].getBoundsInRoot()
        assertTrue("the title stays on one line ($long)", long.right - long.left > long.bottom - long.top)
        assertTrue("the title keeps its width next to a long value ($long vs $alone)", long.right - long.left >= alone.right - alone.left - 1.dp)
    }

    @Test fun settingsListsEachPluginWithItsStatusAndSwitch() {
        var opened = 0
        var switched: Boolean? = null
        val p = org.avmedia.gshockGoogleSync.plugins.FakePlugin(status = "Not set up")
        rule.setContent { org.avmedia.gshockGoogleSync.plugins.PluginCard(p, "Not set up", on = true, onSwitch = { switched = it }, onOpen = { opened++ }) }
        rule.onNodeWithText("Home Assistant").assertIsDisplayed()
        rule.onNodeWithText("Not set up", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Home Assistant").performClick()
        assertEquals(1, opened)
        rule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertEquals(false, switched)
    }

    @Test fun theActionsCardShowsThePluginsLineOnlyWhileOn() {
        val p = org.avmedia.gshockGoogleSync.plugins.FakePlugin()
        var on by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent { org.avmedia.gshockGoogleSync.plugins.PluginActionCard(p, on, onSwitch = { on = it }, onOpenSettings = {}) }
        rule.onNodeWithText("Watch button:", substring = true).assertExists()
        on = false
        rule.waitForIdle()
        rule.onNodeWithText("Watch button:", substring = true).assertDoesNotExist()
    }
}
