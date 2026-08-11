package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.avmedia.gshockGoogleSync.R

@Composable
fun PlayPauseView(
    onUpdate: (ActionsViewModel.TogglePlayPauseAction) -> Unit,
    actionsViewModel: ActionsViewModel = rememberActionsViewModel()
) {
    val playPauseAction = remember {
        actionsViewModel.getAction(ActionsViewModel.TogglePlayPauseAction::class.java)
    }

    var isEnabled by remember { mutableStateOf(playPauseAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.play_pause),
        resourceId = R.drawable.play_pause,
        infoText = stringResource(id = R.string.play_pause_info),
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue
            playPauseAction.enabled = newValue
            onUpdate(playPauseAction)
        }
    )
}
