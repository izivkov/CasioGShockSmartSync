package org.avmedia.gshockGoogleSync.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.mockk
import org.avmedia.gshockGoogleSync.plugins.PluginsContent
import org.avmedia.gshockGoogleSync.ui.settings.AboutContent
import org.avmedia.gshockGoogleSync.ui.settings.AppLogContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.NotificationSettingsContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.NotificationSettingsUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.TideContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.TideUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchFaceContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchFaceUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthSettingsContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutActivityContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutActivityUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutListUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutSettingsListContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen opened from Settings has the standard back arrow at the top start, and no Back button of its own: the
 * arrow, the system back button and the back gesture all go back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class SubScreenBackTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val up = "Navigate up"

    private fun hasArrowAndNoBackButton(content: @Composable () -> Unit) {
        rule.setContent(content)
        rule.onNodeWithContentDescription(up).assertExists()
        rule.onAllNodesWithText("Back").assertCountEquals(0)
    }

    @Test fun theArrowShowsTheTitleAndGoesBack() {
        var back = 0
        rule.setContent { AppSubScreen(title = "A title", onBack = { back++ }) { Text("Body") } }
        rule.onNodeWithText("A title").assertExists()
        rule.onNodeWithText("Body").assertExists()
        rule.onNodeWithContentDescription(up).performClick()
        assertEquals(1, back)
    }

    @Test fun theSystemBackDoesWhatTheArrowDoes() {
        var back = 0
        rule.setContent { AppSubScreen(title = "A title", onBack = { back++ }) { Text("Body") } }
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle { assertEquals(1, back) }
    }

    @Test fun aRealScreenWiresTheArrowToItsOnBack() {
        var back = 0
        rule.setContent { WatchFaceContent(WatchFaceUi(), { back++ }, {}, {}) }
        rule.onNodeWithContentDescription(up).performClick()
        assertEquals(1, back)
    }

    @Test fun watchFace() = hasArrowAndNoBackButton { WatchFaceContent(WatchFaceUi(), {}, {}, {}) }

    @Test fun tide() = hasArrowAndNoBackButton { TideContent(TideUi(), {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> }) }

    @Test fun notificationSettings() = hasArrowAndNoBackButton { NotificationSettingsContent(NotificationSettingsUi(), {}, mockk(relaxed = true)) }

    @Test fun watchHealthSettings() = hasArrowAndNoBackButton { WatchHealthSettingsContent(WatchHealthUi(), {}, mockk(relaxed = true)) }

    @Test fun activitySettingsList() = hasArrowAndNoBackButton { WorkoutSettingsListContent(WorkoutListUi(), {}, {}, {}) }

    @Test fun activitySettingsOfOneActivity() = hasArrowAndNoBackButton { WorkoutActivityContent(WorkoutActivityUi(), {}, mockk(relaxed = true)) }

    @Test fun appLog() = hasArrowAndNoBackButton { AppLogContent(emptyList(), null, {}, {}, {}, {}) }

    @Test fun about() = hasArrowAndNoBackButton { AboutContent("42.9.30", "1.4.2", "0.1.0", onOpenAppLog = {}, onBack = {}) }

    @Test fun plugins() = hasArrowAndNoBackButton { PluginsContent(emptyList(), onBack = {}) }
}
