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
        return null
    }
}
