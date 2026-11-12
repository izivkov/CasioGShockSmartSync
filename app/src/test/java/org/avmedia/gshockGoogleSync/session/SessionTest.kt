package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var gateway: FakeConnectionGateway
    private lateinit var session: Session

    @Before
    fun setUp() {
        gateway = FakeConnectionGateway()
        session = Session(id = "s1", gateway = gateway)
    }

    // --- close hooks -------------------------------------------------------------------------

    @Test
    fun closeHookRunsOnceWhenSessionCloses() {
        var runs = 0
        session.registerCloseHook { runs++ }

        session.close("done")
        session.close("again")

        assertEquals(1, runs)
    }

    @Test
    fun unregisteredCloseHookDoesNotRun() {
        var runs = 0
        val handle = session.registerCloseHook { runs++ }

        handle.close()
        session.close("done")

        assertEquals(0, runs)
    }

    @Test
    fun unregisteringTwiceOrAfterCloseIsHarmless() {
        var runs = 0
        val handle = session.registerCloseHook { runs++ }

        handle.close()
        handle.close()
        session.close("done")
        handle.close()

        assertEquals(0, runs)
    }

    @Test
    fun unregisteringOneHookKeepsTheOthers() {
        val ran = mutableListOf<String>()
        session.registerCloseHook { ran += "a" }
        val b = session.registerCloseHook { ran += "b" }
        session.registerCloseHook { ran += "c" }

        b.close()
        session.close("done")

        assertEquals(listOf("a", "c"), ran)
    }

    @Test
    fun hookRegisteredAfterCloseRunsImmediately() {
        session.close("done")

        var runs = 0
        val handle = session.registerCloseHook { runs++ }

        assertEquals(1, runs)
        handle.close() // the handle of an already-run hook does nothing
        assertEquals(1, runs)
    }

    @Test
    fun failingHookDoesNotStopTheOthersOrTheClose() {
        var secondRan = false
        session.registerCloseHook { error("boom") }
        session.registerCloseHook { secondRan = true }

        session.close("done")

        assertTrue(secondRan)
        assertTrue(session.state.value.phase is SessionPhase.Closed)
    }

    // --- closing -----------------------------------------------------------------------------

    @Test
    fun closeClearsPendingSavesAndRunningActions() {
        session.setReady(button = ConnectionButton.NORMAL)
        session.markPendingSave(ComponentId.ALARMS)
        session.markPendingSave(ComponentId.SETTINGS)
        session.beginRunActions()
        assertTrue(session.state.value.hasPendingSaves())

        session.close("done")

        assertFalse(session.state.value.hasPendingSaves())
        assertFalse(session.isPendingSave(ComponentId.ALARMS))
        assertFalse(session.state.value.isRunningActions)
    }

    @Test
    fun closeIsIdempotentAndTheFirstReasonWins() {
        session.close("first")
        session.close("second", isError = true)

        assertEquals(SessionPhase.Closed("first", isError = false), session.state.value.phase)
    }

    @Test
    fun closeCancelsTheSessionScope() {
        assertTrue(session.sessionScope.isActive)

        session.close("done")

        assertFalse(session.sessionScope.isActive)
        assertFalse(session.isActive)
    }

    @Test
    fun errorCloseDisconnectsTheConnection() {
        session.close("boom", isError = true)

        assertEquals(1, gateway.disconnectCount)
    }

    @Test
    fun normalCloseWithoutDeviceTouchesNothingInTheGateway() {
        session.close("done")

        assertEquals(0, gateway.disconnectCount)
        assertTrue(gateway.tornDown.isEmpty())
    }

    @Test
    fun closeTearsDownTheDeviceHint() {
        val device = fakeDevice("AA:AA")

        session.close("done", deviceHint = device)

        assertEquals(listOf(device), gateway.tornDown)
    }

    @Test
    fun closeTearsDownTheAttachedDevice() {
        val device = fakeDevice("AA:AA")
        session.attachDevice(device)

        session.close("done")

        assertEquals(listOf(device), gateway.tornDown)
    }

    // --- closed sessions stay closed ---------------------------------------------------------

    @Test
    fun closedSessionCannotBecomeReadyAgain() {
        session.close("done")

        assertFalse(session.setReady(button = ConnectionButton.NORMAL))

        assertTrue(session.state.value.phase is SessionPhase.Closed)
        assertNull(session.button)
    }

    @Test
    fun closedSessionIgnoresPendingMarksAndWatchUpdates() {
        session.close("done")

        session.markPendingSave(ComponentId.ALARMS)
        session.updateWatch { it.copy(name = "GW-B5600") }
        session.markSetupComplete()
        session.attachDevice(fakeDevice("AA:AA"))
        session.beginRunActions()

        assertFalse(session.state.value.hasPendingSaves())
        assertEquals("", session.state.value.watch.name)
        assertFalse(session.state.value.isSetupComplete)
        assertNull(session.device)
        assertFalse(session.state.value.isRunningActions)
    }

    // --- ready, button -----------------------------------------------------------------------

    @Test
    fun becomesReadyOnlyOnceAndKeepsItsFirstButton() {
        assertTrue(session.setReady(button = ConnectionButton.FROM_ACTION))
        assertFalse(session.setReady(button = ConnectionButton.FIND))

        assertEquals(ConnectionButton.FROM_ACTION, session.button)
    }

    @Test
    fun buttonIsKeptAfterTheSessionCloses() {
        session.setReady(button = ConnectionButton.ALWAYS_CONNECTED)

        session.close("done")

        assertEquals(ConnectionButton.ALWAYS_CONNECTED, session.button)
    }

    @Test
    fun enterMainNavigationMovesAReadySessionToTheNormalEntry() {
        session.setReady(button = ConnectionButton.ALWAYS_CONNECTED)
        assertEquals(
            SessionPhase.Ready(EntryReason.CONTENT_SELECTOR),
            session.state.value.phase
        )

        session.enterMainNavigation()

        assertEquals(SessionPhase.Ready(EntryReason.NORMAL), session.state.value.phase)
        assertEquals(ConnectionButton.ALWAYS_CONNECTED, session.button)
    }

    @Test
    fun runActionsOnlyAppliesWhileReady() {
        session.beginRunActions()
        assertFalse(session.state.value.isRunningActions)

        session.setReady(button = ConnectionButton.ALWAYS_CONNECTED)
        session.beginRunActions()
        assertTrue(session.state.value.isRunningActions)

        session.endRunActions()
        assertFalse(session.state.value.isRunningActions)
    }

    @Test
    fun pendingSavesAreTrackedPerComponent() {
        session.markPendingSave(ComponentId.ALARMS)
        assertTrue(session.isPendingSave(ComponentId.ALARMS))
        assertFalse(session.isPendingSave(ComponentId.SETTINGS))

        session.clearPendingSave(ComponentId.ALARMS)
        assertFalse(session.isPendingSave(ComponentId.ALARMS))
    }

    @Test
    fun deviceCanBeAttachedAfterCreation() {
        val device = fakeDevice("AA:AA")
        assertNull(session.device)

        session.attachDevice(device)

        assertSame(device, session.device)
    }
}