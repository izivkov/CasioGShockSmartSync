package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.session.ConnectionButton
import org.avmedia.gshockGoogleSync.session.EntryReason
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.session.SessionState
import org.avmedia.gshockGoogleSync.ui.health.resolveDistanceKm
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppScreenStateTest {
    private fun screenFor(state: SessionState) = AppScreenState.navigationFor(state)

    private fun ready(
        entry: EntryReason,
        button: ConnectionButton = ConnectionButton.NORMAL,
        runningActions: Boolean = false
    ) = SessionState(
        phase = SessionPhase.Ready(entry),
        button = button,
        isRunningActions = runningActions
    )

    @Test
    fun startsOnInitialScreen() {
        assertEquals(AppScreen.INITIAL, screenFor(SessionState()).screen)
    }

    @Test
    fun connectingStaysOnPreConnectionScreen() {
        val state = SessionState(phase = SessionPhase.Connecting)
        assertEquals(AppScreen.PRE_CONNECTION, screenFor(state).screen)
    }

    @Test
    fun readySessionOpensTheScreenForItsEntryReason() {
        assertEquals(AppScreen.MAIN_NAVIGATION, screenFor(ready(EntryReason.NORMAL)).screen)
        assertEquals(AppScreen.CONTENT_SELECTOR, screenFor(ready(EntryReason.CONTENT_SELECTOR)).screen)
        assertEquals(AppScreen.RUN_ACTIONS, screenFor(ready(EntryReason.RUN_ACTIONS)).screen)
    }

    @Test
    fun runningActionsShowsRunActionsScreenThenReturnsToEntryScreen() {
        val running = ready(EntryReason.CONTENT_SELECTOR, ConnectionButton.ALWAYS_CONNECTED, runningActions = true)
        assertEquals(AppScreen.RUN_ACTIONS, screenFor(running).screen)

        assertEquals(
            AppScreen.CONTENT_SELECTOR,
            screenFor(running.copy(isRunningActions = false)).screen
        )
    }

    @Test
    fun connectionEndingNormallyReturnsToPreConnectionScreen() {
        val state = SessionState(
            phase = SessionPhase.Closed("Watch Disconnected"),
            button = ConnectionButton.NORMAL
        )
        assertEquals(AppScreen.PRE_CONNECTION, screenFor(state).screen)
    }

    @Test
    fun failedAttemptStaysOnPreConnectionScreen() {
        val state = SessionState(phase = SessionPhase.Closed("Connection Failed"))
        assertEquals(AppScreen.PRE_CONNECTION, screenFor(state).screen)
        assertNull(screenFor(state).errorMessage)
    }

    @Test
    fun errorGoesToPreConnectionScreenAndRetainsMessage() {
        val state = SessionState(
            phase = SessionPhase.Closed("Connection failed", isError = true),
            button = ConnectionButton.NORMAL
        )

        assertEquals(AppScreen.PRE_CONNECTION, screenFor(state).screen)
        assertEquals("Connection failed", screenFor(state).errorMessage)
    }

    @Test
    fun blankErrorReasonHasNoMessage() {
        val state = SessionState(phase = SessionPhase.Closed("", isError = true))
        assertNull(screenFor(state).errorMessage)
    }

    @Test
    fun entryReasonFollowsTheConnectionButton() {
        assertEquals(EntryReason.NORMAL, EntryReason.forButton(ConnectionButton.NORMAL))
        assertEquals(EntryReason.RUN_ACTIONS, EntryReason.forButton(ConnectionButton.FROM_ACTION))
        assertEquals(EntryReason.RUN_ACTIONS, EntryReason.forButton(ConnectionButton.AUTOMATIC_SYNC))
        assertEquals(EntryReason.CONTENT_SELECTOR, EntryReason.forButton(ConnectionButton.FIND))
        assertEquals(EntryReason.NORMAL, EntryReason.forButton(ConnectionButton.ALWAYS_CONNECTED))
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

    @Test
    fun alwaysConnectedConnectionNavigatesToMainNavigation() {
        assertEquals(AppScreen.MAIN_NAVIGATION, screenFor(ready(EntryReason.NORMAL)).screen)
    }

    @Test
    fun foregroundDoesNotChangeOtherScreens() {
        assertEquals(AppScreen.RUN_ACTIONS, AppScreenState.navigationFor(ready(EntryReason.RUN_ACTIONS), appInForeground = true).screen)
        assertEquals(AppScreen.INITIAL, AppScreenState.navigationFor(SessionState(), appInForeground = true).screen)
        val running = ready(EntryReason.BACKGROUND, ConnectionButton.ALWAYS_CONNECTED, runningActions = true)
        assertEquals(AppScreen.RUN_ACTIONS, AppScreenState.navigationFor(running, appInForeground = true).screen)
    }
}

class HaScreenStateTest {
    private val ready = SessionState(
        phase = SessionPhase.Ready(EntryReason.NORMAL),
        button = ConnectionButton.ALWAYS_CONNECTED
    )

    @Test
    fun homeAssistantScreenStaysWhateverTheWatchDoes() {
        val closed = ready.copy(phase = SessionPhase.Closed("Watch disconnected"))
        assertEquals(AppScreen.PLUGIN, AppScreenState.navigationFor(ready, pluginOpen = true).screen)
        assertEquals(AppScreen.PLUGIN, AppScreenState.navigationFor(closed, pluginOpen = true).screen)
        assertEquals(AppScreen.PLUGIN, AppScreenState.navigationFor(SessionState(), pluginOpen = true).screen)
        assertEquals(AppScreen.MAIN_NAVIGATION, AppScreenState.navigationFor(ready, pluginOpen = false).screen)
    }
}
