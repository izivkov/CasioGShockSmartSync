package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.model.ActivitySeconds
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSummary
import org.avmedia.gshockapi.model.StepCounterData
import java.time.LocalDate

/** Builds what the Health screen shows from the stored history, so it is not empty while a LifeLog read runs. */
object StoredHealth {
    /** The newest stored day with its hours; null when nothing is stored. */
    fun stepData(days: List<DayRow>, hoursOfDay: (String) -> List<HourRow>): StepCounterData? {
        val day = days.maxByOrNull { it.date } ?: return null
        val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return null
        val byHour = MutableList<Int?>(24) { null }
        hoursOfDay(day.date).forEach { if (it.hour in 0..23) byHour[it.hour] = it.steps }
        val hr = if (day.hrRest != null || day.hrMin != null || day.hrMax != null) HeartRateSummary(day.hrMax, day.hrMin, day.hrRest) else null
        val activity = if (day.walkSec != null || day.runSec != null || day.stillSec != null || day.activeMin != null) {
            ActivitySeconds(
                runSeconds = day.runSec ?: 0, walkSeconds = day.walkSec ?: 0, cycleSeconds = 0, zeroSeconds = 0, otherSeconds = 0,
                stillSeconds = day.stillSec ?: 0, activeSeconds = (day.activeMin ?: 0) * 60,
            )
        } else null
        return StepCounterData(
            timestamp = date.atStartOfDay(),
            dayOfWeek = date.dayOfWeek.value, month = date.monthValue, dayOfMonth = date.dayOfMonth,
            hourlySteps = byHour, dailyHistory = emptyList(), currentDaySteps = day.steps, distanceMeters = day.distanceM,
            hourlyByHour = byHour,
            h5600 = H5600HealthData(energyKcal = day.kcal, heartRate = hr, activity = activity),
        )
    }
}
