package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.avmedia.gshockGoogleSync.AppScreen
import org.avmedia.gshockGoogleSync.AppScreenState
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The H5600 drops its link about every 45 s; a drop must not end the visit (owner, 2026-11-22). */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkDropTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val watch = mockk<BluetoothDevice> { every { address } returns "AA:BB" }
    private val other = mockk<BluetoothDevice> { every { address } returns "CC:DD" }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun manager(name: String = "GBX-H5600", button: ConnectionButton = ConnectionButton.ALWAYS_CONNECTED): Pair<SessionManager, Session> {
        val m = SessionManager(mockk<GShockRepository>(relaxed = true))
        val s = m.onConnecting(watch)
        m.onDeviceName(name)
        s.setReady(button = button)
        s.enterMainNavigation() // the user is on the main screen
        s.markPendingSave(ComponentId.ALARMS)
        return m to s
    }

    private fun screen(m: SessionManager) = AppScreenState.navigationFor(m.sessionState.value).screen

    @Test fun dropAndReconnectOfTheSameWatchKeepsScreenAndSessionData() = runTest(dispatcher) {
        val (m, s) = manager()
        m.onDisconnected(watch, "Watch Disconnected")
        assertEquals(LinkState.RECONNECTING, m.sessionState.value.link)
        assertEquals(AppScreen.MAIN_NAVIGATION, screen(m))
        advanceTimeBy(60_000)
        m.onConnecting(watch)
        m.onSetupComplete(watch)
        assertEquals(LinkState.UP, m.sessionState.value.link)
        assertSame(s, m.activeSession)
        assertEquals(AppScreen.MAIN_NAVIGATION, screen(m))
        assertTrue(m.sessionState.value.hasPendingSaves())
        assertEquals("GBX-H5600", m.sessionState.value.watch.name)
        advanceTimeBy(Session.RECONNECT_WINDOW_MS * 2) // the old timer must not fire after the return
        assertEquals(LinkState.UP, m.sessionState.value.link)
    }

    @Test fun aDropLongerThanThreeMinutesShowsDisconnectedWithoutLeavingTheScreen() = runTest(dispatcher) {
        val (m, _) = manager()
        m.onDisconnected(watch, "Watch Disconnected")
        advanceTimeBy(Session.RECONNECT_WINDOW_MS - 1)
        assertEquals(LinkState.RECONNECTING, m.sessionState.value.link)
        advanceTimeBy(2)
        assertEquals(LinkState.LOST, m.sessionState.value.link)
        assertEquals(AppScreen.MAIN_NAVIGATION, screen(m))
        m.onDisconnected(null, "Connection Failed") // failed retries while away change nothing
        assertEquals(AppScreen.MAIN_NAVIGATION, screen(m))
        m.onSetupComplete(watch) // it comes back much later
        assertEquals(LinkState.UP, m.sessionState.value.link)
    }

    @Test fun aWatchWithButtonConnectStillEndsTheVisit() = runTest(dispatcher) {
        val (m, _) = manager(name = "GW-B5600", button = ConnectionButton.NORMAL)
        m.onDisconnected(watch, "Watch Disconnected")
        assertEquals(AppScreen.INITIAL, screen(m))
    }

    @Test fun anotherWatchComingBackDoesNotResumeTheVisit() = runTest(dispatcher) {
        val (m, s) = manager()
        m.onDisconnected(watch, "Watch Disconnected")
        m.onSetupComplete(other)
        assertTrue(!s.isActive)
    }
}
