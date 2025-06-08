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
fun VoiceAssistView(
    onUpdate: (ActionsViewModel.StartVoiceAssistAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val voiceAssistAction = remember {
        actionsViewModel.getAction(ActionsViewModel.StartVoiceAssistAction::class.java)
    }

    var isEnabled by remember { mutableStateOf(voiceAssistAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.start_voice_assistant),
        resourceId = R.drawable.voice_assist,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue
            voiceAssistAction.enabled = newValue
            onUpdate(voiceAssistAction.copy(enabled = newValue))
        }
    )
}
