package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockGoogleSync.history.WorkoutTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVariablesTest {
    @Test fun mapsTheLastWorkoutToWorkoutVariables() {
        val v = WorkoutVariables.values(WorkoutTestData.session())
        assertEquals("Running", v["workout.sport"])
        assertEquals("2025-11-22T08:26:40Z", v["workout.start"])
        assertEquals(1800, v["workout.duration_seconds"])
        assertEquals(5.25, v["workout.distance"])
        assertEquals(410L, v["workout.kcal"])
        assertEquals(142, v["workout.hr_avg"])
        assertEquals(171, v["workout.hr_max"])
        assertTrue((v["workout.unconfirmed"] as String).contains("distance: likely km, unconfirmed"))
    }

    @Test fun noWorkoutMeansEveryValueIsNullNotZero() {
        val v = WorkoutVariables.values(null)
        assertEquals(WorkoutVariables.NAMES, v.keys.toList())
        assertTrue(v.values.all { it == null })
    }

    @Test fun aMissingValueIsNullAndEveryNameHasADescription() {
        assertNull(WorkoutVariables.values(WorkoutTestData.session(kcal = null))["workout.kcal"])
        assertEquals(WorkoutVariables.NAMES.toSet(), WorkoutVariables.DESCRIPTIONS.keys)
        assertTrue(WorkoutVariables.DESCRIPTIONS.getValue("workout.distance").contains("UNCONFIRMED"))
    }

    @Test fun theNewTriggerFiresOnlyHooksThatAskForIt() {
        assertEquals("workout_downloaded", WebhookTrigger.WORKOUT.eventName)
        assertTrue(Webhook(onWorkout = true).firesOn(WebhookTrigger.WORKOUT))
        assertFalse(Webhook(onWatchAction = true, onSync = true, onHealthData = true).firesOn(WebhookTrigger.WORKOUT))
        assertFalse(Webhook(onWorkout = true).firesOn(WebhookTrigger.HEALTH_DATA))
        assertEquals(listOf("Workout"), WebhookLabels.triggers(Webhook(onWatchAction = false, onWorkout = true)))
    }

    @Test fun registryResolvesTheVariablesFromTheLastWorkout() {
        val env = WatchVariables.Env(
            api = io.mockk.mockk(relaxed = true), features = io.mockk.mockk(relaxed = true), raw = io.mockk.mockk(relaxed = true),
            isH5600 = { true }, phoneBattery = { 50 }, lastSync = { null }, lastWorkout = { WorkoutTestData.session() },
        )
        val registry = WatchVariables.registry(env)
        assertTrue(WorkoutVariables.NAMES.all { it in registry.names })
        val ctx = VariableContext("workout_downloaded", java.time.OffsetDateTime.now(), false)
        val values = kotlinx.coroutines.runBlocking { registry.resolve(ctx, WorkoutVariables.NAMES.toSet()) }
        assertEquals("Running", values["workout.sport"])
        assertEquals(171, values["workout.hr_max"])
    }
}
