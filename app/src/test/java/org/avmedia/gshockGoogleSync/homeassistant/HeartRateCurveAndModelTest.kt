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

    @Test fun theCurveGoesToHomeAssistantAsAttributesOfTheRecentHeartRate() {
        val slots = listOf(HeartRateSlot(day.atTime(3, 10), 48), HeartRateSlot(day.atTime(9, 0), 120), HeartRateSlot(day.atTime(11, 42), 154))
        val values = HealthVariables.values(data(H5600HealthData(heartRateSlots = slots)))
        val attrs = HaSensors.attributes(values).getValue("hr_recent_avg")
        val curve = attrs["hr_by_slot"] as List<*>
        assertEquals(288, curve.size)
        assertEquals(48, curve[38]) // 03:10 is slot 38
        assertEquals("2026-11-23T11:42:00", attrs["hr_max_at"])
        assertEquals("2026-11-23T03:10:00", attrs["hr_min_at"])
        assertEquals(154, attrs["hr_curve_max"])
        assertEquals(48, attrs["hr_curve_min"])
    }

    @Test fun aDayWithoutHeartRateSendsNoCurve() {
        val values = HealthVariables.values(data(H5600HealthData()))
        assertNull(values["health.hr_by_slot"])
        assertFalse(HaSensors.attributes(values)["hr_recent_avg"].orEmpty().containsKey("hr_by_slot"))
    }

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

    @Test fun otherModelsGetNoneOfTheH5600Sensors() {
        val other = HaSensors.forCaps(HaSensors.ALL, WatchCaps.ALL - WatchCap.H5600).map { it.uniqueId }
        listOf("spo2", "cardio_load", "breathing_exercise", "sleep_score", "workout_sport", "stairs_up").forEach { assertFalse(it, it in other) }
        assertTrue("battery" in other)
        assertTrue("steps_today" in other)
        assertEquals(HaSensors.ALL, HaSensors.forCaps(HaSensors.ALL, WatchCaps.ALL))
    }

    @Test fun aJobCarriesTheModelAndAnOlderJobMeansAllSensors() {
        val job = HaJobs.job(HaTrigger.SYNC, java.time.OffsetDateTime.parse("2026-11-23T10:00:00Z"), "W", mapOf("battery" to 50), emptyMap(), "d", "r", h5600 = false)!!
        assertEquals(false, job.h5600)
        assertNull(HaJob().h5600)
    }
}
