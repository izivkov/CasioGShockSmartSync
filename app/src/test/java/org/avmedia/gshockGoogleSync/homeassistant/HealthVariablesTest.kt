package org.avmedia.gshockGoogleSync.homeassistant

import java.time.LocalDateTime
import org.avmedia.gshockapi.model.ActivityPeriod
import org.avmedia.gshockapi.model.ActivitySeconds
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSamples
import org.avmedia.gshockapi.model.HeartRateSummary
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shaped like what H5600HealthDecoder.decode returns (see its test: header 5306 steps, 5-minute slots, current stats). */
class HealthVariablesTest {
    private val data = StepCounterData(
        timestamp = LocalDateTime.of(2026, 11, 22, 14, 35, 0),
        hourlySteps = List(24) { if (it == 14) 120 else null },
        dailyHistory = emptyList(),
        currentDaySteps = 5306,
        hourlyByHour = List(24) { if (it == 14) 120 else null },
        hourlyIntervals = listOf(
            ActivityPeriod(index = 0, steps = 30, intensity = IntArray(0)),
            ActivityPeriod(index = 1, steps = 0, intensity = IntArray(0)),
            // index 2 was skipped by the decoder (implausible value)
            ActivityPeriod(index = 3, steps = 90, intensity = IntArray(0)),
        ),
        h5600 = H5600HealthData(
            energyKcal = 254,
            energyIsProvisional = true,
            heartRate = HeartRateSummary(maxBpm = 141, minBpm = 52, restingBpm = 58),
            recentHeartRate = HeartRateSamples(count = 4, minBpm = 66, maxBpm = 78, averageBpm = 71),
            activity = ActivitySeconds(
                runSeconds = 0, walkSeconds = 3600, cycleSeconds = 600, zeroSeconds = 0,
                otherSeconds = 60, stillSeconds = 20000, activeSeconds = 4260,
            ),
            stepGoal = 8000,
            lifeLogPending = false,
        ),
    )

    @Test
    fun mapsTheDecodedLifeLogToHealthVariables() {
        val v = HealthVariables.values(data)
        assertEquals(5306, v["health.steps_today"])
        assertEquals("2026-11-22T14:35:00", v["health.recorded_at"])
        assertEquals(254, v["health.kcal_today"])
        assertEquals(58, v["health.hr_rest"])
        assertEquals(52, v["health.hr_min"])
        assertEquals(141, v["health.hr_max"])
        assertEquals(71, v["health.hr_recent_avg"])
        assertEquals(66, v["health.hr_recent_min"])
        assertEquals(78, v["health.hr_recent_max"])
        assertEquals(3600, v["health.walk_seconds"])
        assertEquals(600, v["health.cycle_seconds"])
        assertEquals(60, v["health.other_seconds"])
        assertEquals(20000, v["health.still_seconds"])
        assertEquals(4260, v["health.active_seconds"])
        assertEquals(71, v["health.active_minutes"])
        assertEquals(8000, v["health.step_goal"])
        assertEquals(false, v["health.lifelog_pending"])
    }

    @Test
    fun slotsAreACompactNewestFirstArrayWithGapsAsNull() {
        val v = HealthVariables.values(data)
        assertEquals(listOf(30, 0, null, 90), v["health.slots"])
        assertEquals(5, v["health.slot_minutes"])
        assertEquals(data.hourlyByHour, v["health.steps_by_hour"])
    }

    @Test
    fun noDataOrMissingPartsGiveNullNotAcrash() {
        assertTrue(HealthVariables.values(null).values.all { it == null })
        val bare = HealthVariables.values(StepCounterData(hourlySteps = emptyList(), dailyHistory = emptyList(), currentDaySteps = null))
        assertNull(bare["health.steps_today"])
        assertNull(bare["health.hr_rest"])
        assertNull(bare["health.slots"])
        assertNull(bare["health.active_minutes"])
    }

    @Test
    fun theKcalDescriptionSaysItIsUnconfirmed() {
        val kcal = HealthVariables.DESCRIPTIONS.getValue("health.kcal_today")
        assertTrue(kcal.contains("unconfirmed", ignoreCase = true))
        assertFalse(HealthVariables.NAMES.isEmpty())
        assertEquals(HealthVariables.NAMES.toSet(), HealthVariables.DESCRIPTIONS.keys)
    }

    @Test
    fun descriptionsNameNoOtherAppAndNoIssue() {
        HealthVariables.DESCRIPTIONS.forEach { (name, text) ->
            // Built from parts so this check does not match itself in the private-references scan.
            listOf("casio" + " app", "official" + " app", "upstream issue", "matched the").forEach { word ->
                assertFalse("$name: $text", text.contains(word, ignoreCase = true))
            }
        }
        assertTrue(HealthVariables.DESCRIPTIONS.getValue("health.kcal_today").contains("UNCONFIRMED: scale not verified"))
    }

    @Test
    fun distanceIsTheWatchValueWhenPlausibleElseTheStepEstimate() {
        // 5306 steps: the watch sends no distance, so the Health screen's estimate (0.73 m a step) is used
        assertEquals(3873, HealthVariables.values(data)["health.distance_m"])
        // a watch distance inside 0.4 to 0.85 m a step is taken as it is
        assertEquals(3900, HealthVariables.values(data.copy(distanceMeters = 3900))["health.distance_m"])
        // an implausible one is not
        assertEquals(3873, HealthVariables.values(data.copy(distanceMeters = 90000))["health.distance_m"])
    }

    @Test
    fun noStepsMeansNoDistanceNotZero() {
        assertNull(HealthVariables.values(null)["health.distance_m"])
        assertNull(HealthVariables.values(data.copy(currentDaySteps = null))["health.distance_m"])
        assertTrue(HealthVariables.DESCRIPTIONS.getValue("health.distance_m").contains("estimate", ignoreCase = true))
    }

    @Test fun theDailySummaryGivesTotalRestingAndActiveEnergy() {
        val withSummary = data.copy(h5600 = data.h5600!!.copy(dailySummary = org.avmedia.gshockapi.model.DailySummary(5306, 2400, 1700)))
        val v = HealthVariables.values(withSummary)
        assertEquals(2400L, v["health.energy_total_kcal"])
        assertEquals(1700, v["health.energy_resting_kcal"])
        assertEquals(700L, v["health.energy_active_kcal"])
        assertEquals(254, v["health.kcal_today"])
    }

    @Test fun withoutADailySummaryNoEnergyValueIsMadeUp() {
        val v = HealthVariables.values(data)
        assertNull(v["health.energy_total_kcal"])
        assertNull(v["health.energy_active_kcal"])
    }

    @Test fun aRestingValueAboveTheTotalGivesNoActiveEnergy() {
        val odd = data.copy(h5600 = data.h5600!!.copy(dailySummary = org.avmedia.gshockapi.model.DailySummary(5306, 1000, 1700)))
        assertNull(HealthVariables.values(odd)["health.energy_active_kcal"])
    }
}
