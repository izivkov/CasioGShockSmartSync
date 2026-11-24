package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.homeassistant.HaAction
import org.avmedia.gshockGoogleSync.homeassistant.HaActionPickerContent
import org.avmedia.gshockGoogleSync.homeassistant.HaActionsUi
import org.avmedia.gshockGoogleSync.homeassistant.HaButtonActionContent
import org.avmedia.gshockGoogleSync.ui.actions.ActionsScreen
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Actions screen: all off; all on with the Home Assistant row's second line ("Watch button: ..."); the bottom of the
 * list scrolled above the Save bar; not set up; and the button-action picker.
 */
class ActionsScreenshotTest : ScreenshotTest() {

    private fun viewModel(allOn: Boolean): ActionsViewModel = mockk(relaxed = true) {
        every { isModified } returns MutableStateFlow(allOn)
        every { isEnabled<ActionContainer.Action>(any()) } returns allOn
        every { cameraOrientation() } returns ActionContainer.CameraOrientation.BACK
        every { phoneNumber() } returns "555-123-4567"
    }

    private fun features(): IWatchFeatureManager = mockk(relaxed = true) {
        every { isFeatureSupported(any()) } returns true
    }

    private val goodnight = HaAction("script", "script.goodnight_and_all_lights_off_downstairs", "Goodnight and all lights off downstairs")
    private val items = listOf(
        HaAction("automation", "automation.front_door", "Front door camera"), goodnight,
        HaAction("scene", "scene.movie", "Movie night"), HaAction("script", "script.find_keys", "Find keys"),
    )

    @Composable
    private fun Screen(allOn: Boolean, saved: Boolean = true) {
        CompositionLocalProvider(LocalWatchFeatureManager provides features()) {
            ActionsScreen(
                actionsViewModel = viewModel(allOn),
                haRow = { HaButtonActionContent(saved, allOn, {}, HaActionsUi(items = items, chosen = goodnight), {}, {}, {}) },
            )
        }
    }

    @Test fun actionsLight() = snap("actions", dark = false) { Screen(allOn = false) }
    @Test fun actionsDark() = snap("actions", dark = true) { Screen(allOn = false) }
    @Test fun buttonActionLight() = snap("actions_ha_on", dark = false) { Screen(allOn = true) }
    @Test fun buttonActionDark() = snap("actions_ha_on", dark = true) { Screen(allOn = true) }
    @Test fun notSetUpLight() = snap("actions_ha_not_set_up", dark = false) { Screen(allOn = true, saved = false) }

    private val toBottom = { composeRule.onNodeWithText("555-123-4567").performScrollTo(); Unit }
    @Config(qualifiers = "w360dp-h560dp-420dpi")
    @Test fun bottomLight() = snap("actions_bottom", dark = false, before = toBottom) { Screen(allOn = true) }
    @Config(qualifiers = "w360dp-h560dp-420dpi")
    @Test fun bottomDark() = snap("actions_bottom", dark = true, before = toBottom) { Screen(allOn = true) }

    @Test fun pickerLight() = snap("button_action_picker", dark = false) { HaActionPickerContent(HaActionsUi(items = items, chosen = goodnight), {}, {}, {}) }
    @Test fun pickerDark() = snap("button_action_picker", dark = true) { HaActionPickerContent(HaActionsUi(items = items, chosen = goodnight), {}, {}, {}) }
    @Test fun pickerSearchLight() = snap("button_action_picker_search", dark = false) {
        HaActionPickerContent(HaActionsUi(items = items, chosen = goodnight), {}, {}, {}, initialQuery = "night")
    }
}
