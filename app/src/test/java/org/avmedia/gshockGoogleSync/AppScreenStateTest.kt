package org.avmedia.gshockGoogleSync

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
}