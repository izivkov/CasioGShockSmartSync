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
fun PhoneFinderView(
    onUpdate: (ActionsViewModel.FindPhoneAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val findPhoneAction = remember {
        actionsViewModel.getAction(ActionsViewModel.FindPhoneAction::class.java)
    }

    var isEnabled by remember { mutableStateOf(findPhoneAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.find_phone),
        resourceId = R.drawable.find_phone,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue  // Update local state immediately
            findPhoneAction.enabled = newValue
            onUpdate(findPhoneAction.copy(enabled = newValue))
        }
    )
}
