package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.model.ActivitySeconds
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSummary
import org.avmedia.gshockapi.model.StepCounterData
import java.time.LocalDate
import java.time.LocalDateTime

/** Builds what the Health screen shows from the stored history, so it is not empty while a LifeLog read runs. */
object StoredHealth {
    /**
     * The newest stored day with its hours ([TodayHours]: nothing after [now]); null when nothing is stored. [last] is the
     * time and step goal of the last read, kept outside the history: without it the screen said "synced at 00:00" and the
     * default goal 10000 (owner screenshot 2026-11-23 17:53).
     */
    fun stepData(
        days: List<DayRow>,
        last: LastRead? = null,
        now: LocalDateTime = LocalDateTime.now(),
        hrOfDay: (String) -> List<HrSlotRow> = { emptyList() },
        hoursOfDay: (String) -> List<HourRow>,
    ): StepCounterData? {
        val day = days.maxByOrNull { it.date } ?: return null
        val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return null
        val byHour = TodayHours.hours(emptyList(), hoursOfDay(day.date), date, now)
        val hr = if (day.hrRest != null || day.hrMin != null || day.hrMax != null) HeartRateSummary(day.hrMax, day.hrMin, day.hrRest) else null
        val activity = if (day.walkSec != null || day.runSec != null || day.stillSec != null || day.activeMin != null) {
            ActivitySeconds(
                runSeconds = day.runSec ?: 0, walkSeconds = day.walkSec ?: 0, cycleSeconds = 0, zeroSeconds = 0, otherSeconds = 0,
                stillSeconds = day.stillSec ?: 0, activeSeconds = (day.activeMin ?: 0) * 60,
            )
        } else null
        val at = last?.at?.takeIf { it.toLocalDate() == date } ?: date.atStartOfDay()
        return StepCounterData(
            timestamp = at,
            dayOfWeek = date.dayOfWeek.value, month = date.monthValue, dayOfMonth = date.dayOfMonth,
            hourlySteps = byHour, dailyHistory = emptyList(), currentDaySteps = day.steps, distanceMeters = day.distanceM,
            hourlyByHour = byHour,
            h5600 = H5600HealthData(
                energyKcal = day.kcal, heartRate = hr, activity = activity, stepGoal = last?.stepGoal,
                heartRateSlots = DayHeartRate.merge(emptyList(), hrOfDay(day.date), date, now),
            ),
        )
    }

    /** When the last read was stored and the step goal it carried. */
    data class LastRead(val at: LocalDateTime?, val stepGoal: Int?)
}
