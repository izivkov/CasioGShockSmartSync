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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The H5600 drops its link about every 45 s; a drop must not end the visit. */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkDropTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val watch = mockk<BluetoothDevice> { every { address } returns "AA:BB" }
    private val other = mockk<BluetoothDevice> { every { address } returns "CC:DD" }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

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

    @Test fun aDisconnectionEndsTheSessionAndReturnsToPreConnectionScreen() = runTest(dispatcher) {
        val (m, s) = manager()
        m.onDisconnected(watch, "Watch Disconnected")
        assertFalse(s.isActive)
        assertEquals(AppScreen.PRE_CONNECTION, screen(m))
    }

    @Test fun aWatchWithButtonConnectStillEndsTheVisit() = runTest(dispatcher) {
        val (m, _) = manager(name = "GW-B5600", button = ConnectionButton.NORMAL)
        m.onDisconnected(watch, "Watch Disconnected")
        assertEquals(AppScreen.PRE_CONNECTION, screen(m))
    }

    @Test fun anotherWatchComingBackDoesNotResumeTheVisit() = runTest(dispatcher) {
        val (m, s) = manager()
        m.onDisconnected(watch, "Watch Disconnected")
        m.onSetupComplete(other)
        assertTrue(!s.isActive)
    }
}
