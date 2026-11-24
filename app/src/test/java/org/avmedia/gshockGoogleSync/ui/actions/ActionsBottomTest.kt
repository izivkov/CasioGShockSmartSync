package org.avmedia.gshockGoogleSync.ui.actions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
        for (text in listOf("Watch button: Lights", "555-123-4567")) {
            rule.onNodeWithText(text).performScrollTo()
            val line = rule.onNodeWithText(text).getBoundsInRoot()
            assertTrue("$text ends at ${line.bottom}, Save starts at ${save.top}", line.bottom <= save.top)
        }
    }
}
