package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel.RunEnvironment.VOICE_COMMAND
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject

class VoiceDispatcher @Inject constructor(
    private val actionsViewModel: ActionsViewModel,
    private val api: GShockRepository,
    @ApplicationContext private val context: Context,
    private val intentParser: IntentParser
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun dispatch(text: String) {
        Timber.d("Voice command received: '$text'")
        val command = intentParser.parse(text)
        if (command == null) {
            Timber.w("Could not resolve intent for text: '$text'")
            emitSnackbar("Command not understood")
            return
        }

        val spec = voiceCommandTable[command::class]
        if (spec == null) {
            Timber.w("No routing spec for command: $command")
            emitSnackbar("Command not understood")
            return
        }

        val action = actionsViewModel.getAction(spec.actionClass)
        if (!action.enabled) {
            Timber.w("Action ${action.javaClass.simpleName} is disabled")
            emitSnackbar("Action disabled")
            return
        }

        scope.launch {
            try {
                spec.applyParams(action, command, api)
                ProgressEvents.onNext("NavigateTo", VoiceNavigation(spec.route, command))
                actionsViewModel.runFilteredActions(VOICE_COMMAND)
            } catch (e: Exception) {
                Timber.e(e, "Error executing voice action")
                emitSnackbar("Command not understood")
            }
        }
    }

    private fun emitSnackbar(message: String) {
        scope.launch {
            AppSnackbar(message)
        }
    }
}
