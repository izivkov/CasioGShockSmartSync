package org.avmedia.gshockGoogleSync.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.EventDate
import org.avmedia.gshockapi.model.RepeatPeriod
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class VoiceDispatcher @Inject constructor(
    private val actionsViewModel: ActionsViewModel,
    private val api: GShockRepository,
    private val intentParser: IntentParser,
    private val speechFeedback: VoiceSpeechFeedback,
    private val voiceCommandManager: Provider<VoiceCommandManager>,
    private val eventStorage: org.avmedia.gshockGoogleSync.scratchpad.EventStorage
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentReminder: VoiceCommand.AddReminder? = null

    private val abandonKeywords = listOf("cancel", "stop", "abandon", "abort", "forget it")

    fun dispatch(text: String) {
        Timber.d("Voice command received: '$text'")

        val lowerText = text.lowercase()
        if (abandonKeywords.any { lowerText.contains(it) }) {
            currentReminder = null
            speechFeedback.speak("Canceled")
            return
        }

        if (currentReminder != null) {
            handleReminderConversation(text)
            return
        }

        val command = intentParser.parse(text)
        if (command == null) {
            Timber.w("Could not resolve intent for text: '$text'")
            emitSnackbar("Command not understood")
            speechFeedback.speak("Command not understood")
            return
        }

        if (command is VoiceCommand.Help) {
            val helpText = "You can send commands to your watch using natural language. " +
                    "For example, 'Set alarm at 7:30 am' or 'Set alarm 3 hours from now', or even 'Wake me up in 2 hours', or, " +
                    "'Disable all alarms'. For reminders, you can say 'Set reminder' and the app will interactively ask you about the details. " +
                    "When asked when, you can say something like 'Next Tuesday'. " +
                    "You can also say 'Set timer to 4 minutes and 10 seconds', 'Set auto light', 'Set language to Spanish', and so on. " +
                    "To abort a voice command, just say 'Cancel, abort, or stop'."
            speechFeedback.speak(helpText) {
                listenAgain()
            }
            return
        }

        val spec = voiceCommandTable[command::class]
        if (spec == null) {
            Timber.w("No routing spec for command: $command")
            emitSnackbar("Command not understood")
            speechFeedback.speak("Command not understood")
            return
        }

        if (!spec.isSupported(command)) {
            val message = "This feature is not supported on the watch"
            emitSnackbar(message)
            speechFeedback.speak(message)
            return
        }

        if (command is VoiceCommand.AddReminder) {
            currentReminder = command
            handleReminderConversation("") // Kick off the conversation
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

    private fun handleReminderConversation(text: String) {
        var reminder = currentReminder ?: return

        // 1. Title is required
        if (reminder.title.isNullOrBlank()) {
            if (text.isNotBlank()) {
                reminder = reminder.copy(title = text)
                currentReminder = reminder
                handleReminderConversation("") // Recurse to next step
            } else {
                speechFeedback.speak("What is the reminder for?") {
                    listenAgain()
                }
            }
            return
        }

        // 2. Date is next
        if (reminder.startDate == null) {
            if (text.isNotBlank()) {
                val date = intentParser.parseDate(text)
                if (date != null) {
                    reminder = reminder.copy(startDate = date)
                    currentReminder = reminder
                    handleReminderConversation("") // Recurse to next step
                } else {
                    speechFeedback.speak("I didn't catch the date. When should I remind you?") {
                        listenAgain()
                    }
                }
            } else {
                speechFeedback.speak("When do you want to be reminded?") {
                    listenAgain()
                }
            }
            return
        }

        // 3. Repeat is last
        if (reminder.repeatPeriod == null) {
            if (text.isNotBlank()) {
                val repeat = when {
                    text.contains("week") || text.contains("weekly") -> RepeatPeriod.WEEKLY
                    text.contains("month") || text.contains("monthly") -> RepeatPeriod.MONTHLY
                    text.contains("year") || text.contains("yearly") -> RepeatPeriod.YEARLY
                    text.contains("no") || text.contains("never") || text.contains("don't") || text.contains(
                        "once"
                    ) -> RepeatPeriod.NEVER

                    else -> null
                }
                if (repeat != null) {
                    reminder = reminder.copy(repeatPeriod = repeat)
                    currentReminder = reminder
                    handleReminderConversation("") // Finalize
                } else {
                    speechFeedback.speak("Should this repeat weekly, monthly, or yearly? Or say no.") {
                        listenAgain()
                    }
                }
            } else {
                speechFeedback.speak("Should this repeat weekly, monthly, or yearly? Or say no.") {
                    listenAgain()
                }
            }
            return
        }

        // 4. Finalize
        finalizeReminder(reminder)
        currentReminder = null
    }

    private fun listenAgain() {
        scope.launch {
            delay(100)
            voiceCommandManager.get().startListening(
                onResult = { dispatch(it) },
                onError = { emitSnackbar(it) }
            )
        }
    }

    private fun finalizeReminder(reminder: VoiceCommand.AddReminder) {
        scope.launch {
            try {
                if (!eventStorage.isManualMode()) {
                    speechFeedback.speak("Switching to manual reminders mode")
                    eventStorage.setManualMode(true)
                    eventStorage.save()
                    delay(1000)
                }

                val events = api.getEventsFromWatch()
                val eventIndex =
                    events.indexOfFirst { it.title.isBlank() }.let { if (it == -1) 0 else it }

                val date = reminder.startDate!!
                val newEvent = Event(
                    title = reminder.title!!,
                    startDate = EventDate(date.year, date.month, date.dayOfMonth),
                    endDate = EventDate(
                        date.year + 10,
                        date.month,
                        date.dayOfMonth
                    ), // Set a far future end date for repeats
                    repeatPeriod = reminder.repeatPeriod ?: RepeatPeriod.NEVER,
                    daysOfWeek = if (reminder.repeatPeriod == RepeatPeriod.WEEKLY) arrayListOf(date.dayOfWeek) else null,
                    enabled = true,
                    incompatible = false
                )

                val updatedEvents = events.toMutableList()
                updatedEvents[eventIndex] = newEvent

                api.setEvents(ArrayList(updatedEvents))
                ProgressEvents.onNext("EventsUpdated")

                val dateFeedback =
                    if (date == LocalDate.now()) "today" else if (date == LocalDate.now()
                            .plusDays(1)
                    ) "tomorrow" else "for ${date.month.name.lowercase()} ${date.dayOfMonth}"
                speechFeedback.speak("${reminder.title} added $dateFeedback")
            } catch (e: Exception) {
                Timber.e(e, "Error adding reminder")
                speechFeedback.speak("Failed to add reminder")
            }
        }
    }

    private fun getFeedbackText(command: VoiceCommand): String {
        return when (command) {
            is VoiceCommand.SetAlarm -> {
                val hour12 = if (command.hour % 12 == 0) 12 else command.hour % 12
                val amPm = if (command.hour >= 12) "PM" else "AM"
                val minuteStr =
                    if (command.minute < 10) "0${command.minute}" else "${command.minute}"
                "Alarm set for $hour12:$minuteStr $amPm"
            }

            is VoiceCommand.ClearAllAlarms -> "All alarms cleared"
            is VoiceCommand.DisableAllAlarms -> "All alarms disabled"
            is VoiceCommand.SetTimer -> {
                val parts = mutableListOf<String>()
                if (command.hours > 0) parts.add("${command.hours} ${if (command.hours == 1) "hour" else "hours"}")
                if (command.minutes > 0) parts.add("${command.minutes} ${if (command.minutes == 1) "minute" else "minutes"}")
                if (command.seconds > 0) parts.add("${command.seconds} ${if (command.seconds == 1) "second" else "seconds"}")
                val durationStr = if (parts.isEmpty()) "0 seconds" else parts.joinToString(" ")
                "Timer set for $durationStr"
            }

            is VoiceCommand.SetSetting -> {
                val valueStr = when (command.value) {
                    "true" -> "enabled"
                    "false" -> "disabled"
                    else -> "to ${command.value}"
                }
                "${command.name.replaceFirstChar { it.uppercase() }} $valueStr"
            }

            is VoiceCommand.AddReminder -> "" // Handled in handleReminderConversation
            is VoiceCommand.Help -> "" // Handled in dispatch
        }
    }


    private fun emitSnackbar(message: String) {
        scope.launch {
            AppSnackbar(message)
        }
    }
}
