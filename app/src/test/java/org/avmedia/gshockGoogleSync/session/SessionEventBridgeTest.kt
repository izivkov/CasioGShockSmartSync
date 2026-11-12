package org.avmedia.gshockGoogleSync.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionEventBridgeTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var gateway: FakeConnectionGateway
    private lateinit var manager: SessionManager
    private lateinit var events: FakeEventSource
    private lateinit var bridge: SessionEventBridge

    @Before
    fun setUp() {
        gateway = FakeConnectionGateway()
        manager = SessionManager(gateway)
        events = FakeEventSource()
        bridge = SessionEventBridge(manager)
        bridge.setupEventBridge(events)
    }

    private val session get() = manager.currentSession.value!!
    private val phase get() = session.state.value.phase

    @Test
    fun subscribesOnlyOnceEvenIfSetUpTwice() {
        bridge.setupEventBridge(events)

        assertEquals(1, events.subscribeCount)
    }

    @Test
    fun connectionStartedCreatesAConnectingSessionAndRepeatsKeepIt() {
        events.emit("ConnectionStarted")
        val first = session

        events.emit("ConnectionStarted")

        assertEquals(SessionPhase.Connecting, phase)
        assertSame(first, session)
    }

    @Test
    fun aFullConnectionReachesReadyWithTheButtonOfTheTrigger() {
        gateway.pressed = FakeButton.ACTION
        val device = fakeDevice("AA:AA")

        events.emit("ConnectionStarted")
        events.emit("DeviceName", "CASIO GW-B5600")
        events.emit("DeviceAddress", "AA:AA")
        events.emit("ConnectionSetupComplete", device)
        events.emit("WatchInitializationCompleted")

        val state = session.state.value
        assertEquals(SessionPhase.Ready(EntryReason.RUN_ACTIONS), state.phase)
        assertEquals(ConnectionButton.FROM_ACTION, state.button)
        assertEquals("CASIO GW-B5600", state.watch.name)
        assertEquals("AA:AA", state.watch.address)
        assertTrue(state.isSetupComplete)
        assertSame(device, session.device)
    }

    @Test
    fun blankOrWrongTypedDevicePayloadsAreIgnored() {
        events.emit("ConnectionStarted")

        events.emit("DeviceName", "")
        events.emit("DeviceAddress", 42)
        events.emit("ConnectionSetupComplete", "not a device")

        val state = session.state.value
        assertEquals("", state.watch.name)
        assertEquals("", state.watch.address)
        assertTrue(state.isSetupComplete) // the event itself still completes setup
        assertNull(session.device)
    }

    @Test
    fun disconnectClosesTheSessionAndTearsDownThePayloadDevice() {
        val device = fakeDevice("AA:AA")
        events.emit("WatchInitializationCompleted")

        events.emit("Disconnect", device)

        assertEquals(SessionPhase.Closed("Watch Disconnected", isError = false), phase)
        assertEquals(listOf(device), gateway.tornDown)
    }

    @Test
    fun staleDisconnectIsIgnoredByTheBridgeToo() {
        events.emit("ConnectionStarted")
        events.emit("ConnectionSetupComplete", fakeDevice("AA:AA"))
        events.emit("WatchInitializationCompleted")

        events.emit("Disconnect", fakeDevice("BB:BB"))

        assertTrue(phase is SessionPhase.Ready)
    }

    @Test
    fun connectionFailedClosesTheSessionWithoutAnError() {
        events.emit("ConnectionStarted")

        events.emit("ConnectionFailed")

        val closed = phase as SessionPhase.Closed
        assertFalse(closed.isError)
    }

    @Test
    fun errorEventClosesWithAnErrorAndItsMessage() {
        events.emit("WatchInitializationCompleted")

        events.emit("Error", "Pairing rejected")

        assertEquals(SessionPhase.Closed("Pairing rejected", isError = true), phase)
        assertEquals(1, gateway.disconnectCount)
    }

    @Test
    fun errorEventWithoutAMessageHasABlankReason() {
        events.emit("WatchInitializationCompleted")

        events.emit("Error")

        assertEquals(SessionPhase.Closed("", isError = true), phase)
    }

    @Test
    fun apiErrorIsFatalOnlyWhileConnecting() {
        events.emit("ConnectionStarted")
        events.emit("WatchInitializationCompleted")
        events.emit("ApiError", "Invalid time zone")
        assertTrue(phase is SessionPhase.Ready)

        events.emit("Disconnect")
        events.emit("ConnectionStarted")
        events.emit("ApiError", "Bluetooth is disabled")

        assertEquals(SessionPhase.Closed("Bluetooth is disabled", isError = false), phase)
    }

    @Test
    fun runActionsRequestTurnsOnRunningActionsForAReadySession() {
        events.emit("WatchInitializationCompleted")

        events.emit("RunActions")

        assertTrue(session.state.value.isRunningActions)
    }

    @Test
    fun eachConnectionGetsItsOwnSessionThroughTheBridge() {
        events.emit("ConnectionStarted")
        events.emit("WatchInitializationCompleted")
        val first = session
        events.emit("Disconnect")

        events.emit("ConnectionStarted")
        events.emit("WatchInitializationCompleted")

        assertTrue(first.state.value.phase is SessionPhase.Closed)
        assertTrue(session.state.value.phase is SessionPhase.Ready)
        assertFalse(first === session)
    }
}