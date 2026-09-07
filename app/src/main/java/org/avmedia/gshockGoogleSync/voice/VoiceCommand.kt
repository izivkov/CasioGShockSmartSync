package org.avmedia.gshockGoogleSync.voice

sealed class VoiceCommand {
    data class SetAlarm(val hour: Int, val minute: Int) : VoiceCommand()
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : VoiceCommand()
    data class SetSetting(val name: String, val enabled: Boolean) : VoiceCommand()
}

data class VoiceNavigation(val route: String, val command: VoiceCommand)
