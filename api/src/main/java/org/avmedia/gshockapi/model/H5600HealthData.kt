package org.avmedia.gshockapi.model

import androidx.annotation.Keep

/**
 * Health data from a DW-H5600 LifeLog transfer, beyond the fields every step counter has.
 *
 * Attached to [StepCounterData.h5600]. It is null for the ABL-100 and other watches.
 *
 * Only values with a known source are populated. Energy is provisional: it is the header
 * energy, read as kcal, which matched the watch display within 1 kcal on one day.
 */
@Keep
data class H5600HealthData(
    /** Energy from the transfer header, in kcal, if [energyIsProvisional] holds. */
    val energyKcal: Int? = null,
    val energyIsProvisional: Boolean = true,
    /** Daily heart-rate values the watch stores for the day. */
    val heartRate: HeartRateSummary? = null,
    /** Heart-rate samples from the last few minutes covered by the transfer. */
    val recentHeartRate: HeartRateSamples? = null,
    /** Daily activity time from the current-stats frame, in seconds. Null when not read. */
    val activity: ActivitySeconds? = null,
    /** Daily step target set on the watch. */
    val stepGoal: Int? = null,
    /** True when the watch reports LifeLog data not yet transferred; null when unknown. */
    val lifeLogPending: Boolean? = null,
    /** True when the watch reports workout (ActivityLog) data pending; null when unknown. */
    val activityLogPending: Boolean? = null,
    /** Sleep and Nightly Recharge from sections 0x53 and 0x5A; null on a transfer without them. */
    val sleep: SleepData? = null,
    /** Blood oxygen, breathing exercises, cardio status and stairs; null on a transfer without them. */
    val body: BodyReadings? = null,
)

/** Daily heart-rate values in beats per minute. A field is null when the watch has no valid value. */
@Keep
data class HeartRateSummary(
    val maxBpm: Int?,
    val minBpm: Int?,
    val restingBpm: Int?,
)

/** Summary of heart-rate samples recorded for the last few minutes of the transfer. */
@Keep
data class HeartRateSamples(
    val count: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val averageBpm: Int,
)

/**
 * Activity time for the day, as the watch reports it, in seconds.
 * Walking, cycling and other add up to [activeSeconds] on the watch (checked on 2026-10-26 and 2026-11-16).
 */
@Keep
data class ActivitySeconds(
    val runSeconds: Int,
    val walkSeconds: Int,
    val cycleSeconds: Int,
    val zeroSeconds: Int,
    val otherSeconds: Int,
    val stillSeconds: Int,
    val activeSeconds: Int,
)
