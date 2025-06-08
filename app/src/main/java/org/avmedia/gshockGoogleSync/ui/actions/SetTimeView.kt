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
fun SetTimeView(
    onUpdate: (ActionsViewModel.SetTimeAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val setTimeAction = remember {
        actionsViewModel.getAction(ActionsViewModel.SetTimeAction::class.java)
    }

    // Track enabled state separately
    var isEnabled by remember { mutableStateOf(setTimeAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.set_time),
        resourceId = R.drawable.ic_watch_later_black_24dp,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue  // Update local state immediately
            setTimeAction.enabled = newValue
            onUpdate(setTimeAction.copy(enabled = newValue))
        }
    )
}
