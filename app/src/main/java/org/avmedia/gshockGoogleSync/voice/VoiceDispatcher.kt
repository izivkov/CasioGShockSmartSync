package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel.RunEnvironment.VOICE_COMMAND
import timber.log.Timber
import javax.inject.Inject

class VoiceDispatcher @Inject constructor(
    private val actionsViewModel: ActionsViewModel,
    @ApplicationContext private val context: Context,
    private val intentParser: IntentParser
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun dispatch(text: String) {
        Timber.d("Voice command received: '$text'")
        val resolved = intentParser.parse(text)
        if (resolved?.actionClass != null) {
            try {
                val action = actionsViewModel.getAction(resolved.actionClass)
                Timber.d("Resolved intent to action: ${action.javaClass.simpleName}, enabled: ${action.enabled}")
                if (action.enabled) {
                    updateActionParameters(action, resolved.parameters)
                    actionsViewModel.runFilteredActions(VOICE_COMMAND)
                } else {
                    Timber.w("Action ${action.javaClass.simpleName} is disabled")
                    emitSnackbar("Action disabled")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error executing voice action")
                emitSnackbar("Command not understood")
            }
        } else {
            Timber.w("Could not resolve intent for text: '$text'")
            emitSnackbar("Command not understood")
        }
    }

    private fun updateActionParameters(action: ActionsViewModel.Action, parameters: Map<String, Any>) {
        when (action) {
            is ActionsViewModel.SetAlarmAction -> {
                action.alarmHour = parameters["alarmHour"] as? Int ?: action.alarmHour
                action.alarmMinute = parameters["alarmMinute"] as? Int ?: action.alarmMinute
            }
            is ActionsViewModel.SetSettingsAction -> {
                action.settingName = parameters["setting"] as? String ?: ""
                action.settingValue = parameters["enabled"] as? Boolean ?: false
            }
        }
    }

    private fun emitSnackbar(message: String) {
        scope.launch {
            AppSnackbar(message)
        }
    }
}
