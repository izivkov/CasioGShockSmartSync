package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun FlashlightView(
    onUpdate: (ActionsViewModel.ToggleFlashlightAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    // Access the class type for the action we need
    val classType = ActionsViewModel.ToggleFlashlightAction::class.java

    // Collect the state of actions from the ViewModel
    val actions by actionsViewModel.actions.collectAsState()

    // Retrieve the flashlight action from the ViewModel
    val flashlightAction: ActionsViewModel.ToggleFlashlightAction =
        actionsViewModel.getAction(classType)

    // Remember the enabled state of the flashlight action
    var isEnabled by remember { mutableStateOf(flashlightAction.enabled) }

    // Update `isEnabled` whenever `actions` or `flashlightAction` changes
    LaunchedEffect(actions, flashlightAction) {
        isEnabled = flashlightAction.enabled
    }

    // Display the ActionItem with updated state and handle changes to `isEnabled`
    ActionItem(
        title = stringResource(id = R.string.toggle_flashlight),
        resourceId = R.drawable.flashlight,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            flashlightAction.enabled = newValue
            onUpdate(flashlightAction.copy(enabled = newValue)) // Pass the updated action
        },
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewFlashlight() {
    FlashlightView(onUpdate = {})
}

