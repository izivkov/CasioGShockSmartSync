package org.avmedia.gshockGoogleSync.voice

import org.avmedia.gshockGoogleSync.Screens
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.actions.ActionContainer
import org.avmedia.gshockapi.WatchInfo
import kotlin.reflect.KClass

data class VoiceCommandSpec(
    val route: String,
    val actionClass: Class<out ActionContainer.Action>,
    val isSupported: (VoiceCommand) -> Boolean = { true },
    val featureName: (VoiceCommand) -> String = { "" },
    val applyParams: suspend (ActionContainer.Action, VoiceCommand, GShockRepository) -> Unit,
)

val voiceCommandTable: Map<KClass<out VoiceCommand>, VoiceCommandSpec> = mapOf(
    VoiceCommand.SetAlarm::class to VoiceCommandSpec(
        route = Screens.Alarms.route,
        actionClass = ActionContainer.SetAlarmAction::class.java,
        isSupported = { WatchInfo.alarmCount > 0 },
        featureName = { "Alarms" },
        applyParams = { action, cmd, _ -> (action as ActionContainer.SetAlarmAction).let {
            val c = cmd as VoiceCommand.SetAlarm
            it.alarmHour = c.hour
            it.alarmMinute = c.minute
        }},
    ),
    VoiceCommand.ClearAllAlarms::class to VoiceCommandSpec(
        route = Screens.Alarms.route,
        actionClass = ActionContainer.ClearAllAlarmsAction::class.java,
        isSupported = { WatchInfo.alarmCount > 0 },
        featureName = { "Alarms" },
        applyParams = { _, _, _ -> },
    ),
    VoiceCommand.DisableAllAlarms::class to VoiceCommandSpec(
        route = Screens.Alarms.route,
        actionClass = ActionContainer.DisableAllAlarmsAction::class.java,
        isSupported = { WatchInfo.alarmCount > 0 },
        featureName = { "Alarms" },
        applyParams = { _, _, _ -> },
    ),
    VoiceCommand.SetTimer::class to VoiceCommandSpec(
        route = Screens.Time.route,
        actionClass = ActionContainer.SetTimerAction::class.java,
        featureName = { "Timer" },
        applyParams = { action, cmd, _ -> (action as ActionContainer.SetTimerAction).let {
            val c = cmd as VoiceCommand.SetTimer
            it.timerValueS = (c.hours * 3600) + (c.minutes * 60) + c.seconds
        }},
    ),
    VoiceCommand.SetSetting::class to VoiceCommandSpec(
        route = Screens.Settings.route,
        actionClass = ActionContainer.SetSettingsAction::class.java,
        isSupported = { cmd ->
            val c = cmd as VoiceCommand.SetSetting
            when (c.name) {
                "language" -> WatchInfo.weekLanguageSupported
                "time format" -> WatchInfo.hasTimeFormat
                "date format" -> WatchInfo.hasDateFormat
                "auto light" -> WatchInfo.hasAutoLight
                "power saving" -> WatchInfo.hasPowerSavingMode
                else -> true
            }
        },
        featureName = { (it as VoiceCommand.SetSetting).name },
        applyParams = { action, cmd, _ -> (action as ActionContainer.SetSettingsAction).let {
            val c = cmd as VoiceCommand.SetSetting
            it.settingName = c.name
            it.settingValue = c.value
        }},
    ),
    VoiceCommand.AddReminder::class to VoiceCommandSpec(
        route = Screens.Events.route,
        actionClass = ActionContainer.SetEventsAction::class.java,
        isSupported = { WatchInfo.hasReminders },
        featureName = { "Reminders" },
        applyParams = { _, _, _ -> }, // Handled by VoiceDispatcher.handleReminderConversation
    ),
    VoiceCommand.Help::class to VoiceCommandSpec(
        route = Screens.Time.route,
        actionClass = ActionContainer.SetTimeAction::class.java,
        applyParams = { _, _, _ -> }, // Handled by VoiceDispatcher.dispatch
    ),
    VoiceCommand.SetSettingsToDefault::class to VoiceCommandSpec(
        route = Screens.Settings.route,
        actionClass = ActionContainer.SetSettingsToDefaultAction::class.java,
        applyParams = { _, _, _ -> },
    ),
)
