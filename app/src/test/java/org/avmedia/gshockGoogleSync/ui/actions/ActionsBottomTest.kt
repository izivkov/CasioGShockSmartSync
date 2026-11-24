package org.avmedia.gshockGoogleSync.ui.actions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.homeassistant.HaAction
import org.avmedia.gshockGoogleSync.homeassistant.HaActionsUi
import org.avmedia.gshockGoogleSync.homeassistant.HaButtonActionContent
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On a short screen the Actions list scrolls fully above the Save bar: its last line never sits behind Save. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h560dp-420dpi")
class ActionsBottomTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel: ActionsViewModel = mockk(relaxed = true) {
        every { isModified } returns MutableStateFlow(true)
        every { isEnabled<ActionContainer.Action>(any()) } returns true
        every { cameraOrientation() } returns ActionContainer.CameraOrientation.BACK
        every { phoneNumber() } returns "555-123-4567"
    }
    private val features: IWatchFeatureManager = mockk(relaxed = true) { every { isFeatureSupported(any()) } returns true }
    private val lights = HaAction("script", "script.lights", "Lights")

    @Test fun theLastLinesScrollAboveTheSaveBar() {
        rule.setContent {
            CompositionLocalProvider(LocalWatchFeatureManager provides features) {
                ActionsScreen(
                    actionsViewModel = viewModel,
                    haRow = { HaButtonActionContent(true, true, {}, HaActionsUi(items = listOf(lights), chosen = lights), {}, {}, {}) },
                )
            }
        }
        val save = rule.onNodeWithText("Save").getBoundsInRoot()
        rule.onNodeWithText("Watch button: Lights").performScrollTo()
        val ha = rule.onNodeWithText("Watch button: Lights").getBoundsInRoot()
        assertTrue("the Home Assistant line ends at ${ha.bottom}, Save starts at ${save.top}", ha.bottom <= save.top)
        // Scrolled to the very end, the last row ends with room to spare above the Save bar.
        repeat(5) { rule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        val last = rule.onNodeWithText("555-123-4567").getBoundsInRoot()
        assertTrue("the last line ends at ${last.bottom}, Save starts at ${save.top}", last.bottom + 16.dp <= save.top)
    }
}
