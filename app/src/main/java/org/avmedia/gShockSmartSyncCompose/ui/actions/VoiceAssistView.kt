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
fun VoiceAssistView(
    onUpdate: (ActionsViewModel.StartVoiceAssistAction) -> Unit,
    actionsViewModel: ActionsViewModel = viewModel(),
) {
    val classType = ActionsViewModel.StartVoiceAssistAction::class.java

    val actions by actionsViewModel.actions.collectAsState()
    val startVoiceAssistAction: ActionsViewModel.StartVoiceAssistAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(startVoiceAssistAction.enabled) }

    LaunchedEffect(actions, startVoiceAssistAction) {
        isEnabled = startVoiceAssistAction.enabled
    }

    ActionItem(
        title = stringResource(id = R.string.start_voice_assistant),
        resourceId = R.drawable.voice_assist,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            startVoiceAssistAction.enabled = newValue
            onUpdate(startVoiceAssistAction.copy(enabled = isEnabled))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewVoiceAssist() {
    VoiceAssistView(onUpdate = {})
}

