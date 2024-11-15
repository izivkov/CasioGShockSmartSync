package org.avmedia.gShockSmartSyncCompose.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun RemindersView(
    onUpdate: (ActionsViewModel.SetEventsAction) -> Unit,
    actionsViewModel: ActionsViewModel = viewModel(),
) {
    val classType = ActionsViewModel.SetEventsAction::class.java

    val actions by actionsViewModel.actions.collectAsState()
    val setEventsAction: ActionsViewModel.SetEventsAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(setEventsAction.enabled) }

    LaunchedEffect(actions, setEventsAction) {
        isEnabled = setEventsAction.enabled
    }

    ActionItem(
        title = stringResource(id = R.string.set_reminders),
        resourceId = R.drawable.events,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            setEventsAction.enabled = newValue
            onUpdate(setEventsAction.copy(enabled = isEnabled))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewReminders() {
    RemindersView(onUpdate = {})
}

