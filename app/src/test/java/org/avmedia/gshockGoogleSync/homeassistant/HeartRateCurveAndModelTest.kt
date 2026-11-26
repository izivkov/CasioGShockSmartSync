package org.avmedia.gshockGoogleSync.homeassistant


import org.avmedia.gshockGoogleSync.watchvalues.HealthVariables
import java.time.LocalDate
import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.CardioStatus
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSlot
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The day's heart rate curve as attributes of the recent heart rate sensor, today's cardio load, and the sensors per model. */
class HeartRateCurveAndModelTest {
    private val day = LocalDate.of(2026, 11, 23)

    private fun data(h: H5600HealthData) = StepCounterData(
        timestamp = day.atTime(12, 0), dayOfWeek = 1, month = 11, dayOfMonth = 23, hourlySteps = emptyList(), dailyHistory = emptyList(), currentDaySteps = null, h5600 = h,
    )

    @Test fun todaysCardioLoadWinsOverTheNewestDailyRecord() {
        val old = CardioStatus(day.minusDays(2), 0.5f, 1, 10, 20)
        val now = CardioStatus(day, 1.25f, 2, 340, 410)
        val v = HealthVariables.values(data(H5600HealthData(body = BodyReadings(cardio = old, cardioNow = now))))
        assertEquals(1.25f, v["health.cardio_load_ratio"])
        assertEquals("2026-11-23", v["health.cardio_date"])
        val only = HealthVariables.values(data(H5600HealthData(body = BodyReadings(cardio = old))))
        assertEquals(0.5f, only["health.cardio_load_ratio"])
        assertEquals("2026-11-21", only["health.cardio_date"])
    }

}
