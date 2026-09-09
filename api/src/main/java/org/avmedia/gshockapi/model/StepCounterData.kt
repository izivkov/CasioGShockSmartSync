package org.avmedia.gshockapi.model

import java.time.LocalDateTime

/**
 * Represents a single hourly activity record from the lifelog.
 */
data class ActivityPeriod(
    val index: Int,
    val steps: Int?,
    val intensity: IntArray,
    val distanceMeters: Int? = null
)

/**
 * ABL-100WE life-log record.
 *
 * `hourlySteps` contains the accumulated steps per interval.
 * `dailyHistory` contains the 14 daily step slots.
 * `dailyDistances` contains the matching daily distance slots.
 * `null` represents the watch's unavailable sentinel rather than a genuine zero-step period.
 */
data class StepCounterData(
    val timestamp: LocalDateTime? = null,
    val dayOfWeek: Int? = null,
    val month: Int? = null,
    val dayOfMonth: Int? = null,
    val hourlySteps: List<Int?>,
    val dailyHistory: List<Int?>,
    val dailyDistances: List<Int?> = emptyList(),
    val currentDaySteps: Int?,
    val distanceMeters: Int? = null,
    val pendingDistanceMeters: Int? = null,
    val totalDistanceMeters: Int? = null,
    val bcdTotalSteps: Int? = null,

    // Detailed ABL-100 lifelog data
    val hourlyIntensities: List<IntArray> = emptyList(),
    val pendingIntensity: IntArray = intArrayOf(),
    val committedDistances: List<Int> = emptyList(),

    // Friendly representations
    val hourlyIntervals: List<ActivityPeriod> = emptyList(),
    val hourlyByHour: List<Int?> = emptyList(),

    val raw: ByteArray? = null,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun unavailable() = StepCounterData(
            timestamp = null,
            dayOfWeek = 0,
            month = 0,
            dayOfMonth = 0,
            hourlySteps = emptyList(),
            dailyHistory = emptyList(),
            dailyDistances = emptyList(),
            currentDaySteps = null,
            distanceMeters = null,
            pendingDistanceMeters = null,
            totalDistanceMeters = null,
            bcdTotalSteps = null,
            raw = null,
            warnings = listOf("step counter unavailable")
        )
    }
}
