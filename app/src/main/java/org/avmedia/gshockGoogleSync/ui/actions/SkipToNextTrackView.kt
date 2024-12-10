package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.translateapi.DynamicResourceApi

@Composable
fun SkipToNextTrackView(
    onUpdate: (ActionsViewModel.NextTrack) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel(),
) {
    val classType = ActionsViewModel.NextTrack::class.java

    val actions by actionsViewModel.actions.collectAsState()
    val nextTrack: ActionsViewModel.NextTrack =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(nextTrack.enabled) }

    LaunchedEffect(actions, nextTrack) {
        isEnabled = nextTrack.enabled
    }

    ActionItem(
        title = DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.next_track),
        resourceId = R.drawable.skip_next,
        infoText = DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.skip_to_next_track_info),
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            nextTrack.enabled = newValue
            onUpdate(nextTrack.copy(enabled = isEnabled))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewSkipToNextTrack() {
    SkipToNextTrackView(onUpdate = {})
}

