package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.ui.health.resolveDistanceKm
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
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

        val actual = resolveDistanceKm(stepData, strideMeters = 0.76f)
        val expected = (5306f * 0.76f) / 1000f

        assertEquals(expected, actual, 0.05f)
    }
}