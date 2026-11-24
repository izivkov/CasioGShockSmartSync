package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.mockk
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthSettingsContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthUi
import org.avmedia.gshockapi.io.SmartAlarmCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The smart alarm lives on the Alarms tab, for the H5600 family only, and no longer in Watch health settings. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class SmartAlarmCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theAlarmsTabHasTheSmartAlarmForTheH5600FamilyOnly() {
        assertTrue(smartAlarmShownFor("CASIO GBX-H5600"))
        assertTrue(smartAlarmShownFor("CASIO DW-H5600"))
        assertFalse(smartAlarmShownFor("CASIO GW-B5600"))
        assertFalse(smartAlarmShownFor("CASIO OCW-S7000"))
        assertFalse(smartAlarmShownFor(""))
    }

    @Test fun theCardShowsTimeOnSnoozeAndWakeWindow() {
        rule.setContent { SmartAlarmCard(SmartAlarmUi(alarm = SmartAlarmCodec.Setting(6, 30, true, false, 30)), SmartAlarmEvents()) }
        rule.onNodeWithText("Smart alarm").assertIsDisplayed()
        rule.onNodeWithText("06:30").assertIsDisplayed()
        rule.onNodeWithText("Snooze").assertIsDisplayed()
        rule.onNodeWithText("30 min").assertIsDisplayed()
    }

    @Test fun watchHealthSettingsNoLongerHasTheSmartAlarm() {
        rule.setContent { WatchHealthSettingsContent(WatchHealthUi(), {}, mockk(relaxed = true)) }
        rule.onNodeWithText("Smart alarm", substring = true).assertDoesNotExist()
    }
}
