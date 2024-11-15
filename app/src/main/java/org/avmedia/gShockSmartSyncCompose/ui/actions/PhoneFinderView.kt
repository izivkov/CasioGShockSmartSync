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
fun PhoneFinderView(
    onUpdate: (ActionsViewModel.FindPhoneAction) -> Unit,
    actionsViewModel: ActionsViewModel = viewModel()
) {
    val classType = ActionsViewModel.FindPhoneAction::class.java
    val actions by actionsViewModel.actions.collectAsState()
    val findPhoneAction: ActionsViewModel.FindPhoneAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(findPhoneAction.enabled) }

    LaunchedEffect(actions, findPhoneAction) {
        isEnabled = findPhoneAction.enabled
    }

    ActionItem(
        title = stringResource(id = R.string.find_phone),
        resourceId = R.drawable.find_phone,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            findPhoneAction.enabled = newValue
            onUpdate(findPhoneAction.copy(enabled = newValue))
        },
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPhoneFinderView() {
    PhoneFinderView(onUpdate = {})
}

