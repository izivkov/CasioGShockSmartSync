package org.avmedia.gshockGoogleSync.homeassistant

import java.time.format.DateTimeFormatter
import org.avmedia.gshockGoogleSync.history.DayHeartRate
import org.avmedia.gshockapi.model.StepCounterData

/**
 * `health.hr_*` variables of the day's heart rate curve (section 0x52, merged with the stored day by [DayHeartRate]).
 * Part of [HealthVariables]; null when the day has no heart rate slot.
 */
object HeartRateDayVariables {
    val DESCRIPTIONS: Map<String, String> = linkedMapOf(
        "health.hr_by_slot" to "Heart rate of the day per 5 minutes, 288 entries from 00:00 watch local time (mean of the watch's 2-minute values; null = no value or after now)",
        "health.hr_max_at" to "When the day's highest heart rate of the curve was measured, watch local time, ISO 8601 without offset",
        "health.hr_min_at" to "When the day's lowest heart rate of the curve was measured, watch local time, ISO 8601 without offset",
        "health.hr_curve_max" to "Highest 2-minute heart rate of the day's curve, bpm",
        "health.hr_curve_min" to "Lowest 2-minute heart rate of the day's curve, bpm",
    )

    fun values(data: StepCounterData?): Map<String, Any?> {
        val slots = data?.h5600?.heartRateSlots.orEmpty()
        val high = DayHeartRate.highest(slots)
        val low = DayHeartRate.lowest(slots)
        return linkedMapOf(
            "health.hr_by_slot" to slots.takeIf { it.isNotEmpty() }?.let { DayHeartRate.perFiveMinutes(it) },
            "health.hr_max_at" to high?.time?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "health.hr_min_at" to low?.time?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "health.hr_curve_max" to high?.bpm,
            "health.hr_curve_min" to low?.bpm,
        )
    }
}
