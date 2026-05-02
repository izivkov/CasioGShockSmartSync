package com.beamburst.casswatch.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R

@Composable
fun SkipToNextTrackView(
    onUpdate: (ActionsViewModel.NextTrack) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val nextTrackAction = remember {
        actionsViewModel.getAction(ActionsViewModel.NextTrack::class.java)
    }

    var isEnabled by remember { mutableStateOf(nextTrackAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.next_track),
        resourceId = R.drawable.skip_next,
        infoText = stringResource(id = R.string.skip_to_next_track_info),
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue
            nextTrackAction.enabled = newValue
            onUpdate(nextTrackAction.copy(enabled = newValue))
        }
    )
}
