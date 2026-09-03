package org.avmedia.gshockGoogleSync.voice

import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber
import javax.inject.Inject

data class ResolvedIntent(
    val actionClass: Class<out ActionsViewModel.Action>?,
    val parameters: Map<String, Any> = emptyMap()
)

class IntentParser @Inject constructor() {

    private val alarmPatterns = listOf(
        Regex("wake me up at (.*)", RegexOption.IGNORE_CASE),
        Regex("set alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("set an alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("alarm at (.*)", RegexOption.IGNORE_CASE)
    )

    private val reminderPatterns = listOf(
        Regex("remind me to (.*)", RegexOption.IGNORE_CASE),
        Regex("remind me (.*)", RegexOption.IGNORE_CASE),
        Regex("reminder (.*)", RegexOption.IGNORE_CASE),
        Regex("add reminder (.*)", RegexOption.IGNORE_CASE)
    )

    private val settingsPatterns = listOf(
        Regex("enable (.*)", RegexOption.IGNORE_CASE),
        Regex("disable (.*)", RegexOption.IGNORE_CASE),
        Regex("turn (on|off) (.*)", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): ResolvedIntent? {
        Timber.d("Parsing text: '$text'")
        val cleanedText = text.trim().removeSuffix(".")

        val alarmMatch = alarmPatterns.firstNotNullOfOrNull { it.find(cleanedText) }
        if (alarmMatch != null) {
            val timeString = alarmMatch.groupValues[1]
            Timber.d("Matched Alarm pattern. Extracted time string: '$timeString'")
            val time = parseTime(timeString)
            if (time != null) {
                return ResolvedIntent(
                    ActionsViewModel.SetAlarmAction::class.java,
                    mapOf("alarmHour" to time.hour, "alarmMinute" to time.minute)
                )
            } else {
                Timber.w("Failed to parse time string: '$timeString'")
            }
        }

        val reminderMatch = reminderPatterns.firstNotNullOfOrNull { it.find(cleanedText) }
        if (reminderMatch != null) {
            val content = reminderMatch.groupValues[1]
            Timber.d("Matched Reminder pattern. Extracted content: '$content'")
            return ResolvedIntent(
                ActionsViewModel.SetEventsAction::class.java,
                mapOf("label" to content)
            )
        }

        val settingsMatch = settingsPatterns.firstNotNullOfOrNull { it.find(cleanedText) }
        if (settingsMatch != null) {
            val target = settingsMatch.groupValues.last().lowercase()
            Timber.d("Matched Settings pattern. Extracted target: '$target'")
            if (target.contains("auto light") || target.contains("power saving")) {
                val actionWord = settingsMatch.groupValues[1].lowercase()
                val enabled = actionWord == "enable" || actionWord == "on"
                return ResolvedIntent(
                    ActionsViewModel.SetSettingsAction::class.java,
                    mapOf("setting" to target, "enabled" to enabled)
                )
            }
        }

        Timber.w("No matching pattern found for: '$cleanedText'")
        return null
    }

    private fun parseTime(timeStr: String): LocalTime? {
        val normalized = timeStr.trim().lowercase()
            .replace(Regex("\\s+"), " ") // normalize multiple spaces
            .replace(Regex("([ap])\\.?m\\.?"), "$1m") // "a.m." or "a.m" -> "am"
            .removeSuffix(".")

        Timber.d("Attempting to parse normalized time: '$normalized'")

        // 1. Try Regex for flexible "3am", "3 am", "3:30pm", etc.
        
        // Pattern for "h:mm am/pm"
        val fullTimeRegex = Regex("(\\d{1,2}):(\\d{2})\\s*(am|pm)?")
        fullTimeRegex.find(normalized)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val marker = match.groupValues[3]

            if (marker == "pm" && hour < 12) hour += 12
            if (marker == "am" && hour == 12) hour = 0
            
            if (hour in 0..23 && minute in 0..59) {
                return LocalTime.of(hour, minute)
            }
        }

        // Pattern for "h am/pm"
        val simpleTimeRegex = Regex("(\\d{1,2})\\s*(am|pm)")
        simpleTimeRegex.find(normalized)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val marker = match.groupValues[2]

            if (marker == "pm" && hour < 12) hour += 12
            if (marker == "am" && hour == 12) hour = 0
            
            if (hour in 0..23) {
                return LocalTime.of(hour, 0)
            }
        }

        // 2. Fallback to standard formats (e.g., military time "14:30" or "7")
        val formats = listOf("H:mm", "H", "HH:mm")
        for (format in formats) {
            try {
                return LocalTime.parse(normalized, DateTimeFormatter.ofPattern(format, Locale.US))
            } catch (e: Exception) {}
        }

        return null
    }
}
