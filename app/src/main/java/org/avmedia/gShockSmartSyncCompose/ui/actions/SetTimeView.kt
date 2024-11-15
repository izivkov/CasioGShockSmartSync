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
fun SetTimeView(
    onUpdate: (ActionsViewModel.SetTimeAction) -> Unit,
    actionsViewModel: ActionsViewModel = viewModel(),
) {
    val classType = ActionsViewModel.SetTimeAction::class.java

    val actions by actionsViewModel.actions.collectAsState()
    val setTimeAction: ActionsViewModel.SetTimeAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(setTimeAction.enabled) }

    LaunchedEffect(actions, setTimeAction) {
        isEnabled = setTimeAction.enabled
    }

    ActionItem(
        title = stringResource(id = R.string.set_time),
        resourceId = R.drawable.ic_watch_later_black_24dp,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            setTimeAction.enabled = newValue
            onUpdate(setTimeAction.copy(enabled = isEnabled))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewSetTime() {
    SetTimeView(onUpdate = {})
}

