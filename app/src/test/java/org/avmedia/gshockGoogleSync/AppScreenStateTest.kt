package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.ui.time.TimeState
import org.avmedia.gshockGoogleSync.ui.time.applyExerciseProfileState
import org.avmedia.gshockGoogleSync.ui.time.resolveConfirmedExerciseProfile
import org.avmedia.gshockGoogleSync.ui.time.resolveDistanceKm
import org.avmedia.gshockapi.io.ExerciseIOFunctional
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScreenStateTest {
    @Test
    fun startsOnInitialScreen() {
        assertEquals(AppScreen.INITIAL, AppScreenState().state.value.screen)
    }

    @Test
    fun eventCallbacksUpdateScreenState() {
        val screenState = AppScreenState()

        screenState.showRunActionsScreen()
        assertEquals(AppScreen.RUN_ACTIONS, screenState.state.value.screen)

        screenState.showPreConnectionScreen()
        assertEquals(AppScreen.PRE_CONNECTION, screenState.state.value.screen)

        screenState.showInitialScreen()
        assertEquals(AppScreen.INITIAL, screenState.state.value.screen)
    }

    @Test
    fun errorStateRetainsMessage() {
        val screenState = AppScreenState()

        screenState.showError("Connection failed")

        assertEquals(AppScreen.ERROR, screenState.state.value.screen)
        assertEquals("Connection failed", screenState.state.value.errorMessage)
    }

    @Test
    fun rejectsImplausibleWatchDistanceForCurrentSteps() {
        val stepData = StepCounterData(
            hourlySteps = emptyList(),
            dailyHistory = emptyList(),
            currentDaySteps = 5306,
            distanceMeters = 1900,
        )

        val actual = resolveDistanceKm(stepData)
        val expected = (5306f * 0.73f) / 1000f

        assertTrue(
            "Distance should fall back to stride estimate when raw watch distance is implausible: actual=$actual expected≈$expected",
            kotlin.math.abs(actual - expected) < 0.05f
        )
    }

    @Test
    fun exerciseProfileStatePreservesExistingMetricsWhenNoStepDataExists() {
        val current = TimeState(
            stepCounterData = StepCounterData.unavailable(),
            distanceKm = 1.25f,
            calories = 3.5f
        )
        val profile = ExerciseProfile(stepGoal = 8000, height = 170, weight = 70)

        val updated = applyExerciseProfileState(current, profile)

        assertEquals(8000, updated.stepGoal)
        assertEquals(700, updated.weight)
        assertEquals(1.25f, updated.distanceKm)
        assertEquals(3.5f, updated.calories)
    }

    @Test
    fun usesFreshReadbackWhenItDiffersFromWrittenProfile() {
        val written = ExerciseProfile(stepGoal = 12000, height = 175, weight = 72)
        val freshReadback = ExerciseProfile(stepGoal = 10000, height = 170, weight = 70)

        val confirmed = resolveConfirmedExerciseProfile(written, freshReadback)

        assertEquals(freshReadback, confirmed)
    }

    @Test
    fun weightUsesHundredGramUnitsAcrossProfileRoundTrip() {
        val profile = ExerciseProfile(stepGoal = 6000, height = 170, weight = 626)

        val roundTripped = ExerciseIOFunctional.decode(ExerciseIOFunctional.encode(profile))

        assertEquals(profile, roundTripped)
    }

    @Test
    fun applyExerciseProfileStateUsesHundredGramWeightUnits() {
        val current = TimeState(
            stepCounterData = StepCounterData(
                hourlySteps = emptyList(),
                dailyHistory = emptyList(),
                currentDaySteps = 5000,
                distanceMeters = 4000,
            ),
            distanceKm = 1.5f,
            calories = 2.5f,
        )
        val profile = ExerciseProfile(stepGoal = 6000, height = 170, weight = 626)

        val updated = applyExerciseProfileState(current, profile)

        assertEquals(6000, updated.stepGoal)
        assertEquals(626, updated.weight)
    }
}