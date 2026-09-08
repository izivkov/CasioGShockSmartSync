package org.avmedia.gshockGoogleSync.voice

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber
import javax.inject.Inject

class IntentParser @Inject constructor() {

    private val clearAlarmsPatterns = listOf(
        Regex("clear (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("disable (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("turn off (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("delete (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("remove (?:all )?alarms?", RegexOption.IGNORE_CASE)
    )

    private val alarmPatterns = listOf(
        Regex("(?:set|wake me up|create)(?: an?)? alarm (?:at|for|to) (.*)", RegexOption.IGNORE_CASE),
        Regex("wake me up at (.*)", RegexOption.IGNORE_CASE),
        Regex("set alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("set an alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("alarm at (.*)", RegexOption.IGNORE_CASE)
    )

    private val timerPatterns = listOf(
        Regex("(?:set|start)?\\s*(?:a\\s*)?timer\\s*(?:for|to|of)?\\s*(\\d+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)", RegexOption.IGNORE_CASE),
        Regex("(\\d+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)\\s*timer", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): VoiceCommand? {
        Timber.d("Parsing text: '$text'")
        val cleanedText = text.trim().removeSuffix(".")

        if (clearAlarmsPatterns.any { it.containsMatchIn(cleanedText) }) {
            return VoiceCommand.ClearAllAlarms
        }

        alarmPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            val timeString = match.groupValues[1]
            parseTime(timeString)?.let { time ->
                return VoiceCommand.SetAlarm(time.hour, time.minute)
            }
            Timber.w("Failed to parse time string: '$timeString'")
        }

        timerPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            val amount = match.groupValues[1].toIntOrNull()
            val unit = match.groupValues[2].lowercase()
            if (amount != null) {
                return when {
                    unit.startsWith("hour") || unit.startsWith("hr") -> VoiceCommand.SetTimer(amount, 0, 0)
                    unit.startsWith("minute") || unit.startsWith("min") -> VoiceCommand.SetTimer(0, amount, 0)
                    else -> VoiceCommand.SetTimer(0, 0, amount)
                }
            }
        }

        val lowerText = cleanedText.lowercase()
        if (lowerText.contains("auto light") || lowerText.contains("power saving") || lowerText.contains("light") || lowerText.contains("power save")) {
            val target = when {
                lowerText.contains("auto light") || lowerText.contains("light") -> "auto light"
                else -> "power saving"
            }
            val enabled = !(lowerText.contains("off") || lowerText.contains("disable") || lowerText.contains("disabled"))
            return VoiceCommand.SetSetting(target, enabled)
        }

        Timber.w("No matching pattern found for: '$cleanedText'")
        return null
    }

    private fun parseTime(timeStr: String): LocalTime? {
        val normalized = timeStr.trim().lowercase()
            .replace(Regex("\\s+"), " ") // normalize multiple spaces
            .replace(Regex("([ap])\\.?m\\.?"), "$1m") // "a.m." or "a.m" -> "am"
            .replace(Regex("ap$"), "am")
            .replace(Regex("ap\\s"), "am ")
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
