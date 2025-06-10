package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun FlashlightView(
    onUpdate: (ActionsViewModel.ToggleFlashlightAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val flashlightAction = remember {
        actionsViewModel.getAction(ActionsViewModel.ToggleFlashlightAction::class.java)
    }

    var isEnabled by remember { mutableStateOf(flashlightAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.toggle_flashlight),
        resourceId = R.drawable.flashlight,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue  // Update local state immediately
            flashlightAction.enabled = newValue
            onUpdate(flashlightAction.copy(enabled = newValue))
        }
    )
}
