package org.avmedia.gshockGoogleSync.ui.watchhealth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.avmedia.gshockapi.io.WatchFaceCodec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The face choice offers the faces of the connected model: DW-H5600 1 to 3, GBX-H5600 1 to 4 (Tide). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
class WatchFacesPerModelUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun open(faceCount: Int) {
        rule.setContent { WatchFaceContent(WatchFaceUi(face = WatchFaceCodec.Face(1, listOf(0, 10, 12, 19)), faceCount = faceCount), {}, {}, {}) }
        rule.onNodeWithText("Time only").performClick()
        rule.onNodeWithText("Steps").assertExists()
    }

    @Test fun aDwH5600IsOfferedThreeFaces() {
        open(3)
        rule.onNodeWithText("Tide").assertDoesNotExist()
    }

    @Test fun aGbxH5600IsOfferedTheTideFaceToo() {
        open(4)
        rule.onNodeWithText("Tide").assertExists()
    }
}
