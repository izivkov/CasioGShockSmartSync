package org.avmedia.gshockGoogleSync.ui.watchhealth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.avmedia.gshockapi.io.TideSettingCodec
import org.avmedia.gshockapi.io.TideUserPointCodec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tide: one short attribution line that opens the full credit, "Not set" for a factory point, the height unit as a choice. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class TideScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var feet: Boolean? = null
    private val ui = TideUi(
        setting = TideSettingCodec.Setting(4, 0, 12, false),
        points = listOf(TideUserPointCodec.Point(4, 20, 51.23, 2.93, 1f, false), TideUserPointCodec.FACTORY, null),
        names = listOf("Oostende pier", "", ""),
    )

    private fun show() = rule.setContent { TideContent(ui, {}, {}, {}, { feet = it }, { _, _, _ -> }, { _, _ -> }) }

    @Test fun theAttributionIsOneShortLineThatOpensTheFullCredit() {
        show()
        rule.onNodeWithText("Hart-Davis", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Tide data: TICON-4, CC BY 4.0").assertIsDisplayed().performClick()
        rule.onNodeWithText("Hart-Davis", substring = true).assertExists()
    }

    @Test fun aFactoryPointSaysNotSet() {
        show()
        rule.onNodeWithText("35.60", substring = true).assertDoesNotExist()
        rule.onNodeWithText("09:00").assertDoesNotExist()
        rule.onNodeWithText("Point 2").assertIsDisplayed()
        rule.onNodeWithText("04:20").assertIsDisplayed()
        assertEquals(2, rule.onAllNodesWithText("Not set").fetchSemanticsNodes().size)
    }

    @Test fun theHeightUnitIsAChoice() {
        show()
        rule.onNodeWithText("Heights in feet").assertDoesNotExist()
        rule.onNodeWithText("Height unit").assertIsDisplayed()
        rule.onNodeWithText("Metres").assertIsDisplayed().performClick()
        rule.onNodeWithText("Feet").performClick()
        assertEquals(true, feet)
    }
}
