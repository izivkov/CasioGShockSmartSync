package org.avmedia.gshockGoogleSync.voice

import org.avmedia.gshockapi.model.RepeatPeriod
import java.time.LocalDate

sealed class VoiceCommand {
    data class SetAlarm(val hour: Int, val minute: Int) : VoiceCommand()
    object ClearAllAlarms : VoiceCommand()
    object DisableAllAlarms : VoiceCommand()
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : VoiceCommand()
    data class SetSetting(val name: String, val value: String) : VoiceCommand()
    data class AddReminder(
        val title: String? = null,
        val startDate: LocalDate? = null,
        val repeatPeriod: RepeatPeriod? = null
    ) : VoiceCommand()

    object Help : VoiceCommand()
}

data class VoiceNavigation(val route: String, val command: VoiceCommand)
