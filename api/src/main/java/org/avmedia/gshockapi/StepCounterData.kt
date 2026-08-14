package org.avmedia.gshockapi

/**
 * ABL-100WE life-log record.
 *
 * `hourlySteps` contains the 144 two-byte history slots (six 24-hour blocks),
 * while `dailyHistory` contains the 14 four-byte day slots. `null` represents
 * the watch's unavailable sentinel rather than a genuine zero-step period.
 */
data class StepCounterData(
    val dayOfWeek: Int,
    val month: Int,
    val dayOfMonth: Int,
    val hourlySteps: List<Int?>,
    val dailyHistory: List<Int?>,
    val currentDaySteps: Int?,
) {
    companion object {
        fun unavailable() = StepCounterData(0, 0, 0, emptyList(), emptyList(), null)
    }
}
