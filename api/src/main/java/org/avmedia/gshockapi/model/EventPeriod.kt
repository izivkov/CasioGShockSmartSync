package org.avmedia.gshockapi.model

import androidx.annotation.Keep

@Keep
enum class RepeatPeriod(val periodDuration: String) {
    NEVER("NEVER"),
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY");

    companion object {
        fun fromString(value: String): RepeatPeriod =
            values().find { it.periodDuration == value.uppercase() } ?: NEVER

        fun isRepeating(period: RepeatPeriod): Boolean =
            period != NEVER
    }

    fun toDisplayString(): String = periodDuration.lowercase()
        .replaceFirstChar { it.uppercase() }
}
