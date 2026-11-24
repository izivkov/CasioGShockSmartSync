package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watchvalues.WorkoutVariables
import com.google.gson.JsonParser
import org.avmedia.gshockGoogleSync.history.WorkoutTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "last workout" Home Assistant sensors: what is sent for a workout, and nothing for a missing value. */
class WorkoutSensorsTest {
    private val ids = listOf("workout_sport", "workout_start", "workout_duration", "workout_distance", "workout_kcal", "workout_hr_avg", "workout_hr_max")

    @Test fun theSevenSensorsExistAndBelongToTheHealthGroup() {
        ids.forEach { id ->
            val s = HaSensors.ALL.firstOrNull { it.uniqueId == id }
            assertTrue("$id missing", s != null)
            assertEquals(HaGroup.HEALTH, HaGroups.of(s!!))
        }
    }

    @Test fun aWorkoutBecomesStatesAndTheUnconfirmedAttribute() {
        val values = WorkoutVariables.values(org.avmedia.gshockGoogleSync.history.WorkoutMapper.map(WorkoutTestData.session()).workout)
        val states = HaSensors.states(values)
        assertEquals("Running", states["workout_sport"])
        assertEquals("2025-11-22T08:26:40Z", states["workout_start"])
        assertEquals(30L, states["workout_duration"])
        assertEquals(5.25, states["workout_distance"])
        assertEquals(410L, states["workout_kcal"])
        assertEquals(142, states["workout_hr_avg"])
        assertEquals(171, states["workout_hr_max"])
        val attrs = HaSensors.attributes(values).getValue("workout_sport")
        assertTrue((attrs["unconfirmed"] as String).contains("unconfirmed"))
        val json = JsonParser.parseString(HaPayloads.updateSensorStates(states)).asJsonObject
        assertEquals("update_sensor_states", json.get("type").asString)
        assertEquals(7, json.getAsJsonArray("data").size())
    }

    @Test fun unitsNotConfirmedSayUnconfirmedInTheSensorName() {
        listOf("workout_distance", "workout_kcal").forEach { id ->
            assertTrue(HaSensors.ALL.first { it.uniqueId == id }.name.contains("unconfirmed"))
        }
    }

    @Test fun noWorkoutOrAMissingValueSendsNothingNeverZero() {
        assertTrue(HaSensors.states(WorkoutVariables.values(null)).keys.none { it in ids })
        val states = HaSensors.states(WorkoutVariables.values(org.avmedia.gshockGoogleSync.history.WorkoutMapper.map(WorkoutTestData.session(kcal = null, hrAvg = null)).workout))
        assertFalse("workout_kcal" in states)
        assertFalse("workout_hr_avg" in states)
        assertTrue("workout_hr_max" in states)
    }
}
