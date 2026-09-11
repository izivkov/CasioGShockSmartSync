package org.avmedia.gshockGoogleSync.voice

import org.avmedia.gshockapi.model.RepeatPeriod
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber
import javax.inject.Inject

class IntentParser @Inject constructor() {

    private val clearAlarmsPatterns = listOf(
        Regex("clear (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("delete (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("remove (?:all )?alarms?", RegexOption.IGNORE_CASE)
    )

    private val disableAlarmsPatterns = listOf(
        Regex("disable (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("turn off (?:all )?alarms?", RegexOption.IGNORE_CASE),
        Regex("stop (?:all )?alarms?", RegexOption.IGNORE_CASE)
    )

    private val alarmPatterns = listOf(
        Regex("(?:set|wake me up|create)(?: an?)? alarm (?:at|for|to|in) (.*)", RegexOption.IGNORE_CASE),
        Regex("wake me up (?:at|in) (.*)", RegexOption.IGNORE_CASE),
        Regex("set alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("set an alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("alarm (?:at|in) (.*)", RegexOption.IGNORE_CASE)
    )

    // Gates timer detection: the phrase must mention "timer" somewhere.
    private val timerKeywordPattern = Regex("timer", RegexOption.IGNORE_CASE)

    // Matches every "<amount> <unit>" pair in the phrase (not just one), so a
    // duration like "3 minutes 10 seconds" yields two matches instead of the
    // regex engine skipping ahead to only the last pair.
    private val timerUnitPattern = Regex(
        "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|a|an|half)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)",
        RegexOption.IGNORE_CASE
    )

    // Supports "Set timer at four ten" or "timer 4:10" -> 4 minutes 10 seconds
    private val implicitTimerPattern = Regex(
        "timer.*?(?:at|for|to)?\\s*(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)[:\\s]+(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)",
        RegexOption.IGNORE_CASE
    )

    private val languagePattern = Regex("(?:set|change)?\\s*(?:the\\s*)?language\\s*(?:to)?\\s*(english|spanish|french|german|italian|russian)", RegexOption.IGNORE_CASE)
    private val timeFormatPattern = Regex("(?:set|change)?\\s*(?:the\\s*)?time format\\s*(?:to)?\\s*(12|24) (?:hours?|hrs?)?", RegexOption.IGNORE_CASE)
    private val dateFormatPattern = Regex("(?:set|change)?\\s*(?:the\\s*)?date format\\s*(?:to)?\\s*(month day|day month|month-day|day-month)", RegexOption.IGNORE_CASE)
    private val lightDurationPattern = Regex("(?:set|change)?\\s*(?:the\\s*)?(?:light|illumination) (?:duration|period)\\s*(?:to)?\\s*(1\\.5|2|3|4|5) (?:seconds?|secs?)?", RegexOption.IGNORE_CASE)
    private val buttonTonePattern = Regex("(turn (on|off)|enable|disable|change|set)\\s*(?:the\\s*)?(button sound|button tone|sound)\\s*(?:to)?\\s*(on|off)?", RegexOption.IGNORE_CASE)
    private val settingsDefaultPattern = Regex("(?:set|change|reset)?\\s*(?:the\\s*)?settings(?:\\s*(?:to)?\\s*defaults?)?", RegexOption.IGNORE_CASE)
    private val helpPattern = Regex("help", RegexOption.IGNORE_CASE)

    private val reminderPatterns = listOf(
        Regex("(?:remind me|add (?:a |an )?(?:new )?(?:reminder|event)|create (?:a |an )?(?:new )?(?:reminder|event)|set (?:a |an )?(?:new )?(?:reminder|event)|new reminder)(?: (?:to )?(.*))?", RegexOption.IGNORE_CASE)
    )

    private val fillerRegex = Regex("\\b(um|uh|mm|ah|er|like)\\b", RegexOption.IGNORE_CASE)
    private val correctionMarkers = listOf("i mean", "actually", "no wait", "sorry", "i meant")

    fun parse(text: String): VoiceCommand? {
        Timber.d("Parsing raw text: '$text'")
        
        var processedText = handleSelfCorrection(text)
        processedText = stripFillers(processedText)
        
        val cleanedText = processedText.trim().removeSuffix(".").lowercase()
        Timber.d("Parsed cleaned text: '$cleanedText'")

        // 1. Timer (check before Alarm to avoid "4:10" being treated as a clock time if "timer" is present)
        if (timerKeywordPattern.containsMatchIn(cleanedText)) {
            // explicit units first: "3 minutes 10 seconds"
            val durationSeconds = parseDurationToSeconds(cleanedText)
            if (durationSeconds > 0) {
                val hours = durationSeconds / 3600
                val minutes = (durationSeconds % 3600) / 60
                val seconds = durationSeconds % 60
                Timber.d("Matched explicit timer: ${hours}h ${minutes}m ${seconds}s")
                return VoiceCommand.SetTimer(hours, minutes, seconds)
            }
            
            // fallback to implicit units: "at four ten"
            implicitTimerPattern.find(cleanedText)?.let { match ->
                val minStr = match.groupValues[1]
                val secStr = match.groupValues[2]
                val min = minStr.toIntOrNull() ?: wordToNumber(minStr)
                val sec = secStr.toIntOrNull() ?: wordToNumber(secStr)
                if (min != null && sec != null) {
                    Timber.d("Matched implicit timer: ${min}m ${sec}s")
                    return VoiceCommand.SetTimer(0, min, sec)
                }
            }
        }

        // 2. Alarm
        if (clearAlarmsPatterns.any { it.containsMatchIn(cleanedText) }) {
            Timber.d("Matched clear alarms")
            return VoiceCommand.ClearAllAlarms
        }

        if (helpPattern.containsMatchIn(cleanedText)) {
            Timber.d("Matched help command")
            return VoiceCommand.Help
        }

        if (disableAlarmsPatterns.any { it.containsMatchIn(cleanedText) }) {
            Timber.d("Matched disable alarms")
            return VoiceCommand.DisableAllAlarms
        }

        alarmPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            Timber.d("Matched alarm pattern: ${match.value}")
            val timeString = match.groupValues[1]
            
            // Try absolute time (e.g., "7:30 am")
            parseTime(timeString)?.let { time ->
                return VoiceCommand.SetAlarm(time.hour, time.minute)
            }
            
            // Fallback: try relative duration (e.g., "3 hours from now")
            val durationSeconds = parseDurationToSeconds(timeString)
            if (durationSeconds > 0) {
                val targetTime = LocalTime.now().plusSeconds(durationSeconds.toLong())
                Timber.d("Matched relative alarm: +${durationSeconds}s -> ${targetTime}")
                return VoiceCommand.SetAlarm(targetTime.hour, targetTime.minute)
            }
        }

        // 3. Reminder
        reminderPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            val payload = match.groupValues.getOrNull(1) ?: ""
            val (title, date) = extractTitleAndDate(payload)
            val repeat = extractRepeat(payload)
            return VoiceCommand.AddReminder(if (title.isBlank()) null else title, date, repeat)
        }

        // 4. Settings
        languagePattern.find(cleanedText)?.let {
            val lang = it.groupValues[1].replaceFirstChar { char -> char.uppercase() }
            return VoiceCommand.SetSetting("language", lang)
        }

        timeFormatPattern.find(cleanedText)?.let {
            val format = it.groupValues[1] + "h"
            return VoiceCommand.SetSetting("time format", format)
        }

        dateFormatPattern.find(cleanedText)?.let {
            val raw = it.groupValues[1].replace("-", " ")
            val format = if (raw == "month day") "MM:DD" else "DD:MM"
            return VoiceCommand.SetSetting("date format", format)
        }

        lightDurationPattern.find(cleanedText)?.let {
            val duration = it.groupValues[1] + "s"
            return VoiceCommand.SetSetting("light duration", duration)
        }

        buttonTonePattern.find(cleanedText)?.let {
            val action = it.groupValues[1].lowercase()
            val stateSuffix = it.groupValues.getOrNull(3)?.lowercase()
            
            val enabled = when {
                stateSuffix == "on" -> true
                stateSuffix == "off" -> false
                action.contains("on") || action.contains("enable") -> true
                else -> false
            }
            return VoiceCommand.SetSetting("button tone", enabled.toString())
        }

        if (settingsDefaultPattern.containsMatchIn(cleanedText)) {
            Timber.d("Matched settings default pattern")
            return VoiceCommand.SetSettingsToDefault
        }

        if (cleanedText.contains("auto light") || cleanedText.contains("power saving") || cleanedText.contains("light") || cleanedText.contains("power save")) {
            val target = when {
                cleanedText.contains("auto light") || cleanedText.contains("light") -> "auto light"
                else -> "power saving"
            }
            val enabled = !(cleanedText.contains("off") || cleanedText.contains("disable") || cleanedText.contains("disabled"))
            return VoiceCommand.SetSetting(target, enabled.toString())
        }

        Timber.w("No matching pattern found for: '$cleanedText'")
        return null
    }

    private fun handleSelfCorrection(text: String): String {
        var result = text
        for (marker in correctionMarkers) {
            val lastIndex = result.lowercase().lastIndexOf(marker)
            if (lastIndex != -1) {
                val corrected = result.substring(lastIndex + marker.length).trim()
                if (corrected.isNotBlank()) {
                    Timber.d("Detected self-correction with marker '$marker'. New text: '$corrected'")
                    result = corrected
                }
            }
        }
        return result
    }

    private fun stripFillers(text: String): String {
        val result = fillerRegex.replace(text, "").replace(Regex("\\s+"), " ").trim()
        if (result != text) {
            Timber.d("Stripped fillers. Original: '$text', New: '$result'")
        }
        return result
    }

    private fun extractTitleAndDate(payload: String): Pair<String, LocalDate?> {
        val onInAtPattern = Regex("(.*) (?:on|in|at) (.*)", RegexOption.IGNORE_CASE)
        val match = onInAtPattern.find(payload)
        
        if (match != null) {
            val title = match.groupValues[1].trim()
            val dateStr = match.groupValues[2].trim()
            parseDate(dateStr)?.let {
                return Pair(title, it)
            }
            return Pair(payload.trim(), null)
        }
        return Pair(payload.trim(), null)
    }

    private fun extractRepeat(payload: String): RepeatPeriod? {
        val lower = payload.lowercase()
        return when {
            lower.contains("every week") || lower.contains("weekly") -> RepeatPeriod.WEEKLY
            lower.contains("every month") || lower.contains("monthly") -> RepeatPeriod.MONTHLY
            lower.contains("every year") || lower.contains("yearly") -> RepeatPeriod.YEARLY
            lower.contains("no repeat") || lower.contains("don't repeat") || lower.contains("once") -> RepeatPeriod.NEVER
            else -> null
        }
    }

    fun parseDate(text: String): LocalDate? {
        val lower = text.lowercase()
        val today = LocalDate.now()
        
        if (lower.contains("tomorrow")) return today.plusDays(1)
        if (lower.contains("today")) return today
        
        val daysOfWeek = mapOf(
            "monday" to java.time.DayOfWeek.MONDAY,
            "tuesday" to java.time.DayOfWeek.TUESDAY,
            "wednesday" to java.time.DayOfWeek.WEDNESDAY,
            "thursday" to java.time.DayOfWeek.THURSDAY,
            "friday" to java.time.DayOfWeek.FRIDAY,
            "saturday" to java.time.DayOfWeek.SATURDAY,
            "sunday" to java.time.DayOfWeek.SUNDAY
        )
        
        for ((name, day) in daysOfWeek) {
            if (lower.contains(name)) {
                var target = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
                if (target == today && lower.contains("next")) {
                    target = target.plusWeeks(1)
                } else if (lower.contains("next")) {
                    target = today.with(java.time.temporal.TemporalAdjusters.next(day))
                }
                return target
            }
        }
        
        val months = Month.entries.map { it.name.lowercase() }
        for (monthName in months) {
            if (lower.contains(monthName)) {
                val numberRegex = Regex("(\\d+)")
                numberRegex.find(lower)?.let {
                    val day = it.groupValues[1].toInt()
                    val month = Month.valueOf(monthName.uppercase())
                    val year = if (month.value < today.monthValue || (month.value == today.monthValue && day < today.dayOfMonth)) {
                        today.year + 1
                    } else {
                        today.year
                    }
                    return LocalDate.of(year, month, day)
                }
            }
        }

        return null
    }

    private fun parseDurationToSeconds(text: String): Int {
        val normalized = text.lowercase()
            .replace("an hour and a half", "90 minutes")
            .replace("a hour and a half", "90 minutes")
            .replace("one hour and a half", "90 minutes")
            .replace("half an hour", "30 minutes")
            .replace("half a hour", "30 minutes")
            .replace("an hour", "1 hour")
            .replace("a hour", "1 hour")
            .replace("a minute", "1 minute")
            .replace("a second", "1 second")

        val matches = timerUnitPattern.findAll(normalized).toList()
        if (matches.isEmpty()) return 0

        var totalSeconds = 0
        for (match in matches) {
            val amountStr = match.groupValues[1]
            val unit = match.groupValues[2].lowercase()
            
            val amount = when (amountStr) {
                "half" -> 0.5f
                else -> amountStr.toIntOrNull()?.toFloat() ?: wordToNumber(amountStr)?.toFloat()
            } ?: continue

            val multiplier = when {
                unit.startsWith("hour") || unit.startsWith("hr") -> 3600
                unit.startsWith("minute") || unit.startsWith("min") -> 60
                else -> 1
            }
            totalSeconds += (amount * multiplier).toInt()
        }
        return totalSeconds
    }

    private fun wordToNumber(word: String): Int? {
        return when (word.lowercase()) {
            "a", "an", "one" -> 1
            "two" -> 2
            "three" -> 3
            "four" -> 4
            "five" -> 5
            "six" -> 6
            "seven" -> 7
            "eight" -> 8
            "nine" -> 9
            "ten" -> 10
            else -> null
        }
    }

    private fun parseTime(timeStr: String): LocalTime? {
        val normalized = timeStr.trim().lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("([ap])\\.?m\\.?"), "$1m")
            .replace(Regex("ap$"), "am")
            .replace(Regex("ap\\s"), "am ")
            .removeSuffix(".")

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

        val formats = listOf("H:mm", "H", "HH:mm")
        for (format in formats) {
            try {
                return LocalTime.parse(normalized, DateTimeFormatter.ofPattern(format, Locale.US))
            } catch (e: Exception) {}
        }

        return null
    }
}
