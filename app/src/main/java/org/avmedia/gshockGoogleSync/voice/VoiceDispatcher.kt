package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val intentParser: IntentParser,
    private val speechFeedback: VoiceSpeechFeedback
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun dispatch(text: String) {
        Timber.d("Voice command received: '$text'")
        val command = intentParser.parse(text)
        if (command == null) {
            Timber.w("Could not resolve intent for text: '$text'")
            emitSnackbar("Command not understood")
            speechFeedback.speak("Command not understood")
            return
        }

        val spec = voiceCommandTable[command::class]
        if (spec == null) {
            Timber.w("No routing spec for command: $command")
            emitSnackbar("Command not understood")
            speechFeedback.speak("Command not understood")
            return
        }

        val action = actionsViewModel.getAction(spec.actionClass)
        if (!action.enabled) {
            Timber.w("Action ${action.javaClass.simpleName} is disabled")
            emitSnackbar("Action disabled")
            speechFeedback.speak("Action disabled")
            return
        }

        scope.launch {
            try {
                spec.applyParams(action, command, api)
                ProgressEvents.onNext("NavigateTo", VoiceNavigation(spec.route, command))
                actionsViewModel.runSingleActionSuspend(action)
                
                // Increase delay to 1000ms to ensure the "success" beep of the STT is finished
                delay(1000)
                val feedback = getFeedbackText(command)
                speechFeedback.speak(feedback)
            } catch (e: Exception) {
                Timber.e(e, "Error executing voice action")
                emitSnackbar("Command failed")
                speechFeedback.speak("Command failed")
            }
        }
    }

    private fun getFeedbackText(command: VoiceCommand): String {
        return when (command) {
            is VoiceCommand.SetAlarm -> {
                val hour12 = if (command.hour % 12 == 0) 12 else command.hour % 12
                val amPm = if (command.hour >= 12) "PM" else "AM"
                val minuteStr = if (command.minute < 10) "0${command.minute}" else "${command.minute}"
                "Alarm set for $hour12:$minuteStr $amPm"
            }
            is VoiceCommand.ClearAllAlarms -> "All alarms cleared"
            is VoiceCommand.SetTimer -> {
                val parts = mutableListOf<String>()
                if (command.hours > 0) parts.add("${command.hours} ${if (command.hours == 1) "hour" else "hours"}")
                if (command.minutes > 0) parts.add("${command.minutes} ${if (command.minutes == 1) "minute" else "minutes"}")
                if (command.seconds > 0) parts.add("${command.seconds} ${if (command.seconds == 1) "second" else "seconds"}")
                val durationStr = if (parts.isEmpty()) "0 seconds" else parts.joinToString(" ")
                "Timer set for $durationStr"
            }
            is VoiceCommand.SetSetting -> {
                val state = if (command.enabled) "enabled" else "disabled"
                "${command.name.replaceFirstChar { it.uppercase() }} $state"
            }
        }
    }


    private fun emitSnackbar(message: String) {
        scope.launch {
            AppSnackbar(message)
        }
    }
}
