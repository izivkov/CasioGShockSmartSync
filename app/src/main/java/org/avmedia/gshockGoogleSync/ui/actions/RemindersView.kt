package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun RemindersView(
    onUpdate: (ActionsViewModel.SetEventsAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val setEventsAction = remember {
        actionsViewModel.getAction(ActionsViewModel.SetEventsAction::class.java)
    }

    var isEnabled by remember { mutableStateOf(setEventsAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.set_reminders),
        resourceId = R.drawable.events,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue
            setEventsAction.enabled = newValue
            onUpdate(setEventsAction.copy(enabled = newValue))
        }
    )
}
