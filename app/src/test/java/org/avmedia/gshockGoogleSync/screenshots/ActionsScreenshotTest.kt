package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.homeassistant.HaActionsUi
import org.avmedia.gshockGoogleSync.homeassistant.HaButtonActionContent
import org.avmedia.gshockGoogleSync.ui.actions.ActionsScreen
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.junit.Test

/** Actions screen (the rows) and the button-action picker (all rows on, Find Phone included, Save enabled). */
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

    @Composable
    private fun Screen(allOn: Boolean) {
        CompositionLocalProvider(LocalWatchFeatureManager provides features()) {
            ActionsScreen(
                actionsViewModel = viewModel(allOn),
                haRow = { HaButtonActionContent(allOn, allOn, {}, HaActionsUi(), {}, {}, {}) },
            )
        }
    }

    @Test fun actionsLight() = snap("actions", dark = false) { Screen(allOn = false) }
    @Test fun actionsDark() = snap("actions", dark = true) { Screen(allOn = false) }
    @Test fun buttonActionPickerLight() = snap("button_action_picker", dark = false) { Screen(allOn = true) }
    @Test fun buttonActionPickerDark() = snap("button_action_picker", dark = true) { Screen(allOn = true) }
}
