package org.avmedia.gshockGoogleSync.session

import org.avmedia.gshockGoogleSync.AppNavigationState
import org.avmedia.gshockGoogleSync.AppScreen
import org.avmedia.gshockGoogleSync.AppScreenState
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionManagerTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var gateway: FakeConnectionGateway
    private lateinit var manager: SessionManager

    @Before
    fun setUp() {
        gateway = FakeConnectionGateway()
        manager = SessionManager(gateway)
    }

    private fun phaseOf(session: Session) = session.state.value.phase

    // --- a new session per connection --------------------------------------------------------

    @Test
    fun noSessionBeforeTheFirstConnection() {
        assertNull(manager.currentSession.value)
        assertNull(manager.activeSession)
        assertFalse(manager.isReady)
        assertEquals(SessionPhase.Disconnected, manager.sessionState.value.phase)
    }

    @Test
    fun everyConnectionGetsANewSession() {
        val first = manager.onConnected()
        manager.onDisconnected()
        val second = manager.onConnected()

        assertNotSame(first, second)
        assertNotEquals(first.id, second.id)
        assertTrue(phaseOf(first) is SessionPhase.Closed)
        assertTrue(phaseOf(second) is SessionPhase.Ready)
        assertSame(second, manager.currentSession.value)
    }

    @Test
    fun aClosedSessionIsNeverReused() {
        val first = manager.onConnected()
        manager.onDisconnected()

        val second = manager.onConnected()

        assertTrue(phaseOf(first) is SessionPhase.Closed)
        assertTrue(second.isActive)
    }

    @Test
    fun connectingSessionIsReusedByRepeatedStartsAndByTheConnectedEvent() {
        val connecting = manager.onConnecting()
        assertEquals(SessionPhase.Connecting, phaseOf(connecting))

        assertSame(connecting, manager.onConnecting()) // "ConnectionStarted" fires twice per attempt
        assertSame(connecting, manager.onConnected())
        assertTrue(phaseOf(connecting) is SessionPhase.Ready)
    }

    @Test
    fun aReadySessionIsNotReplacedByALateConnectionStarted() {
        val ready = manager.onConnected()

        assertSame(ready, manager.onConnecting())

        assertTrue(phaseOf(ready) is SessionPhase.Ready)
    }

    @Test
    fun onConnectedIsIdempotent() {
        val first = manager.onConnected()
        val second = manager.onConnected()

        assertSame(first, second)
    }

    @Test
    fun createSessionClosesThePreviousActiveSession() {
        val first = manager.onConnected()

        val second = manager.createSession()

        assertTrue(phaseOf(first) is SessionPhase.Closed)
        assertEquals(SessionPhase.Connecting, phaseOf(second))
    }

    // --- the connection button per trigger ---------------------------------------------------

    @Test
    fun buttonAndEntryReasonFollowTheTrigger() {
        val expected = mapOf(
            FakeButton.NONE to (ConnectionButton.NORMAL to EntryReason.NORMAL),
            FakeButton.ACTION to (ConnectionButton.FROM_ACTION to EntryReason.RUN_ACTIONS),
            FakeButton.AUTO_TIME to (ConnectionButton.AUTOMATIC_SYNC to EntryReason.RUN_ACTIONS),
            FakeButton.FIND_PHONE to (ConnectionButton.FIND to EntryReason.CONTENT_SELECTOR),
            FakeButton.ALWAYS_CONNECTED to (ConnectionButton.ALWAYS_CONNECTED to EntryReason.CONTENT_SELECTOR),
        )

        expected.forEach { (pressed, result) ->
            val (button, entry) = result
            val fresh = SessionManager(FakeConnectionGateway().also { it.pressed = pressed })

            val session = fresh.onConnected()

            assertEquals("button for $pressed", button, session.button)
            assertEquals("entry for $pressed", SessionPhase.Ready(entry), phaseOf(session))
            assertEquals("manager button for $pressed", button, fresh.connectionButton)
        }
    }

    @Test
    fun buttonIsReadWhenTheSessionBecomesReadyNotWhenItIsCreated() {
        gateway.pressed = FakeButton.FIND_PHONE // stale value from the previous connection
        val connecting = manager.onConnecting()
        assertNull(connecting.button)

        gateway.pressed = FakeButton.ACTION
        manager.onConnected()

        assertEquals(ConnectionButton.FROM_ACTION, connecting.button)
    }

    @Test
    fun buttonIsFixedForTheSessionAndRereadForTheNext() {
        gateway.pressed = FakeButton.ACTION
        val first = manager.onConnected()

        gateway.pressed = FakeButton.FIND_PHONE
        manager.onConnected() // a repeated "initialized" event
        assertEquals(ConnectionButton.FROM_ACTION, first.button)

        manager.onDisconnected()
        val second = manager.onConnected()
        assertEquals(ConnectionButton.FIND, second.button)
    }

    // --- disconnects: stale, duplicate, first reason wins ------------------------------------

    @Test
    fun staleDisconnectForAnotherDeviceIsIgnored() {
        val watch = fakeDevice("AA:AA:AA:AA:AA:AA")
        val other = fakeDevice("BB:BB:BB:BB:BB:BB")
        val session = manager.onConnected(device = watch)

        manager.onDisconnected(device = other, reason = "late disconnect of the old watch")

        assertTrue(phaseOf(session) is SessionPhase.Ready)
        assertTrue(gateway.tornDown.isEmpty())
    }

    @Test
    fun staleDisconnectIsIgnoredOnceTheDeviceIsAttachedBySetupComplete() {
        val watch = fakeDevice("AA:AA:AA:AA:AA:AA")
        val other = fakeDevice("BB:BB:BB:BB:BB:BB")
        val session = manager.onConnecting()
        manager.onSetupComplete(watch)
        manager.onConnected()

        manager.onDisconnected(device = other)
        assertTrue(phaseOf(session) is SessionPhase.Ready)

        manager.onDisconnected(device = watch)
        assertTrue(phaseOf(session) is SessionPhase.Closed)
        assertEquals(listOf(watch), gateway.tornDown)
    }

    @Test
    fun disconnectWithoutAKnownDeviceStillClosesTheSession() {
        val session = manager.onConnected()

        manager.onDisconnected(device = fakeDevice("AA:AA"))

        assertTrue(phaseOf(session) is SessionPhase.Closed)
    }

    @Test
    fun theFirstCloseReasonWins() {
        val session = manager.onConnected()

        manager.onDisconnected(reason = "first")
        manager.onDisconnected(reason = "second")
        manager.onError("third")

        assertEquals(SessionPhase.Closed("first", isError = false), phaseOf(session))
    }

    @Test
    fun disconnectWithNoSessionRecordsTheReason() {
        manager.onDisconnected(reason = "Connection Failed")

        val session = manager.currentSession.value
        assertNotNull(session)
        assertEquals(SessionPhase.Closed("Connection Failed", isError = false), phaseOf(session!!))
        assertNull(manager.activeSession)
    }

    // --- pending saves -----------------------------------------------------------------------

    @Test
    fun pendingSavesAreClearedWhenTheSessionCloses() {
        manager.onConnected()
        manager.markPendingSave(ComponentId.ALARMS)
        manager.markPendingSave(ComponentId.EVENTS)
        assertTrue(manager.isPendingSave(ComponentId.ALARMS))
        assertTrue(manager.sessionState.value.isPendingSave(ComponentId.EVENTS))

        manager.onDisconnected()

        assertFalse(manager.isPendingSave(ComponentId.ALARMS))
        assertFalse(manager.sessionState.value.hasPendingSaves())
    }

    @Test
    fun pendingMarksAfterTheSessionClosedAreIgnored() {
        manager.onConnected()
        manager.onDisconnected()

        manager.markPendingSave(ComponentId.SETTINGS)

        assertFalse(manager.isPendingSave(ComponentId.SETTINGS))
    }

    @Test
    fun pendingMarksDoNotCarryOverToTheNextSession() {
        manager.onConnected()
        manager.markPendingSave(ComponentId.ALARMS)
        manager.onDisconnected()

        manager.onConnected()

        assertFalse(manager.isPendingSave(ComponentId.ALARMS))
    }

    @Test
    fun markingWithNoSessionDoesNothing() {
        manager.markPendingSave(ComponentId.ALARMS)
        manager.clearPendingSave(ComponentId.ALARMS)

        assertFalse(manager.isPendingSave(ComponentId.ALARMS))
    }

    // --- errors ------------------------------------------------------------------------------

    @Test
    fun errorClosesWithAnErrorAndReleasesTheConnection() {
        val session = manager.onConnected()

        manager.onError("")

        assertEquals(SessionPhase.Closed("", isError = true), phaseOf(session))
        assertEquals(1, gateway.disconnectCount)
    }

    @Test
    fun apiErrorWhileConnectingClosesTheSessionQuietly() {
        val session = manager.onConnecting()

        manager.onApiError("Bluetooth is disabled")

        assertEquals(SessionPhase.Closed("Bluetooth is disabled", isError = false), phaseOf(session))
    }

    @Test
    fun apiErrorWhileReadyIsIgnored() {
        val session = manager.onConnected()

        manager.onApiError("Invalid time zone")

        assertTrue(phaseOf(session) is SessionPhase.Ready)
    }

    // --- device info and run actions ---------------------------------------------------------

    @Test
    fun deviceInfoUpdatesTheActiveSession() {
        val device = fakeDevice("AA:AA")
        val session = manager.onConnecting()

        manager.onDeviceName("CASIO GW-B5600")
        manager.onDeviceAddress("AA:AA")
        manager.onSetupComplete(device)

        val state = session.state.value
        assertEquals("CASIO GW-B5600", state.watch.name)
        assertEquals("AA:AA", state.watch.address)
        assertTrue(state.isSetupComplete)
        assertSame(device, session.device)
    }

    @Test
    fun deviceInfoWithNoActiveSessionIsIgnored() {
        manager.onDeviceName("CASIO GW-B5600")
        manager.onSetupComplete(fakeDevice("AA:AA"))

        assertNull(manager.currentSession.value)
    }

    @Test
    fun runActionsRequestOnlyAffectsAReadySession() {
        manager.onRunActionsRequested()
        assertNull(manager.currentSession.value)

        val session = manager.onConnecting()
        manager.onRunActionsRequested()
        assertFalse(session.state.value.isRunningActions)

        manager.onConnected()
        manager.onRunActionsRequested()
        assertTrue(session.state.value.isRunningActions)
    }

    // --- navigation follows the session ------------------------------------------------------

    @Test
    fun screenStateFollowsTheSession() {
        val screens = AppScreenState(manager)
        fun screen() = screens.state.value.screen

        assertEquals(AppScreen.INITIAL, screen())

        manager.onConnecting()
        assertEquals(AppScreen.PRE_CONNECTION, screen())

        manager.onConnected()
        assertEquals(AppScreen.MAIN_NAVIGATION, screen())

        manager.onDisconnected()
        assertEquals(AppScreen.INITIAL, screen())

        gateway.pressed = FakeButton.ALWAYS_CONNECTED
        manager.onConnected()
        assertEquals(AppScreen.CONTENT_SELECTOR, screen())

        manager.onRunActionsRequested()
        assertEquals(AppScreen.RUN_ACTIONS, screen())
        manager.activeSession?.endRunActions()
        assertEquals(AppScreen.CONTENT_SELECTOR, screen())

        manager.activeSession?.enterMainNavigation()
        assertEquals(AppScreen.MAIN_NAVIGATION, screen())

        manager.onError("")
        assertEquals(AppNavigationState(AppScreen.PRE_CONNECTION, errorMessage = null), screens.state.value)
    }

    private fun assertNotEquals(a: Any?, b: Any?) = Assert.assertNotEquals(a, b)
}
