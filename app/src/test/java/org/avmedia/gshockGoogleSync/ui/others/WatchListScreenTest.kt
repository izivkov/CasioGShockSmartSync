package org.avmedia.gshockGoogleSync.ui.others

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.avmedia.gshockGoogleSync.session.WatchList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The watch list: the App log is reachable with no watch connected; Back only when it makes sense. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class WatchListScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun theAppLogOpensFromTheWatchListWithNoWatchConnected() {
        rule.setContent {
            WatchListWithLog(log = { Text("The App log screen") }) { openLog -> WatchListHeader(onBack = null, onOpenLog = openLog) }
        }
        rule.onNodeWithText("App log").assertIsDisplayed().performClick()
        rule.onNodeWithText("The App log screen").assertIsDisplayed()
        pressBack()
        rule.onNodeWithText("The App log screen").assertDoesNotExist()
        rule.onNodeWithText("App log").assertIsDisplayed()
    }

    @Test fun theFirstPageHasNoBackButton() {
        rule.setContent { WatchListHeader(onBack = null, onOpenLog = {}) }
        rule.onNodeWithText("Back").assertDoesNotExist()
        rule.onNodeWithText("App log").assertIsDisplayed()
    }

    @Test fun fromTheConnectedScreensWithAWatchConnectedTheBackButtonShows() {
        var back = 0
        rule.setContent { WatchListHeader(onBack = { back++ }, onOpenLog = {}) }
        rule.onNodeWithText("Back").performClick()
        assertEquals(1, back)
    }

    @Test fun backOnlyWhenOpenedFromTheConnectedScreensAndAWatchIsStillConnected() {
        assertTrue(WatchList.showsBack(openedFromConnectedScreens = true, watchConnected = true))
        assertFalse(WatchList.showsBack(openedFromConnectedScreens = true, watchConnected = false))
        assertFalse(WatchList.showsBack(openedFromConnectedScreens = false, watchConnected = true))
        assertFalse(WatchList.showsBack(openedFromConnectedScreens = false, watchConnected = false))
    }
}
