package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** The watch list opened from the connected screens of an always-connected watch. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchListTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val watch = mockk<BluetoothDevice> { every { address } returns "AA:BB:CC:DD:EE:01" }
    private val repository = mockk<GShockRepository>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        WatchList.hide()
    }

    @After fun tearDown() {
        WatchList.hide()
        KeepConnected.resetForTest()
        Dispatchers.resetMain()
    }

    private fun connected(): Pair<SessionManager, Session> {
        val m = SessionManager(repository)
        val s = m.onConnecting(watch)
        m.onDeviceName("GBX-H5600")
        m.onDeviceAddress("AA:BB:CC:DD:EE:01")
        s.setReady(button = ConnectionButton.ALWAYS_CONNECTED)
        s.enterMainNavigation()
        return m to s
    }

    private fun screen(m: SessionManager) =
        AppScreenState.navigationFor(m.sessionState.value, watchListOpen = WatchList.open.value).screen

    @Test fun openingTheListWhileConnectedKeepsTheLinkAndBackReturnsToTheConnectedScreens() = runTest(dispatcher) {
        val (m, s) = connected()
        assertEquals(AppScreen.MAIN_NAVIGATION, screen(m))
        WatchList.show()
        assertEquals("the list shows", AppScreen.WATCH_LIST, screen(m))
        assertTrue("the session stays active", s.isActive)
        assertSame(s, m.activeSession)
        verify(exactly = 0) { repository.disconnect() }
        verify(exactly = 0) { repository.teardownConnection(any()) }
        WatchList.hide() // back
        assertEquals("back returns to the connected screens", AppScreen.MAIN_NAVIGATION, screen(m))
        assertEquals(LinkState.UP, m.sessionState.value.link)
    }

    @Test fun aReconnectWhileTheListIsOpenDoesNotLeaveTheList() = runTest(dispatcher) {
        val (m, _) = connected()
        WatchList.show()
        m.onDisconnected(watch, "Watch Disconnected") // the drop
        assertEquals(LinkState.RECONNECTING, m.sessionState.value.link)
        assertEquals(AppScreen.WATCH_LIST, screen(m))
        m.onConnecting(watch) // kind 14: the watch connects itself again
        assertEquals(AppScreen.WATCH_LIST, screen(m))
        m.onSetupComplete(watch)
        assertEquals(LinkState.UP, m.sessionState.value.link)
        assertEquals("the list stays until the person leaves it", AppScreen.WATCH_LIST, screen(m))
        m.onRunActionsRequested()
        assertEquals(AppScreen.WATCH_LIST, screen(m))
    }

    @Test fun everySessionStateKeepsTheOpenList() {
        val states = listOf(
            SessionState(),
            SessionState(phase = SessionPhase.Connecting),
            SessionState(phase = SessionPhase.Ready(EntryReason.NORMAL), link = LinkState.LOST),
            SessionState(phase = SessionPhase.Ready(EntryReason.BACKGROUND), button = ConnectionButton.ALWAYS_CONNECTED),
            SessionState(phase = SessionPhase.Ready(EntryReason.NORMAL), isRunningActions = true),
            SessionState(phase = SessionPhase.Closed("Another watch chosen"), button = ConnectionButton.ALWAYS_CONNECTED),
            SessionState(phase = SessionPhase.Closed("Failed", isError = true)),
        )
        states.forEach { state ->
            assertEquals(state.toString(), AppScreen.WATCH_LIST, AppScreenState.navigationFor(state, watchListOpen = true).screen)
        }
    }

    @Test fun choosingAnotherWatchDisconnectsTheCurrentOneOnceThenConnectsTheChosenOne() {
        val calls = mutableListOf<String>()
        var connectedTo: String? = "AA:BB:CC:DD:EE:01"
        val switch = WatchSwitch(
            connectedAddress = { connectedTo },
            disconnectCurrent = { calls += "disconnect $connectedTo"; connectedTo = null },
            connect = { calls += "connect $it" },
        )
        WatchList.show()
        switch.choose("AA:BB:CC:DD:EE:02")
        switch.choose("AA:BB:CC:DD:EE:02") // a second tap (or the pairing result and the bond broadcast)
        assertEquals(
            listOf("disconnect AA:BB:CC:DD:EE:01", "connect AA:BB:CC:DD:EE:02", "connect AA:BB:CC:DD:EE:02"),
            calls,
        )
        assertFalse("the old watch may not take the link back", WatchList.admits("AA:BB:CC:DD:EE:01"))
        assertTrue(WatchList.admits("aa:bb:cc:dd:ee:02"))
    }

    @Test fun choosingTheConnectedWatchChangesNothing() {
        val calls = mutableListOf<String>()
        val switch = WatchSwitch({ "AA:BB:CC:DD:EE:01" }, { calls += "disconnect" }, { calls += "connect $it" })
        WatchList.show()
        switch.choose("aa:bb:cc:dd:ee:01")
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun noWatchConnectsOverAPairingInProgress() {
        val calls = mutableListOf<String>()
        var connectedTo: String? = "AA:BB:CC:DD:EE:01"
        val switch = WatchSwitch({ connectedTo }, { calls += "disconnect"; connectedTo = null }, { calls += "connect $it" })
        WatchList.show()
        assertTrue("with nothing chosen the connected watch may reconnect", WatchList.admits("AA:BB:CC:DD:EE:01"))
        switch.startPairing()
        assertEquals(listOf("disconnect"), calls)
        assertFalse(WatchList.admits("AA:BB:CC:DD:EE:01"))
        assertFalse(WatchList.admits("AA:BB:CC:DD:EE:02"))
        switch.choose("AA:BB:CC:DD:EE:02") // the new watch paired
        assertEquals(listOf("disconnect", "connect AA:BB:CC:DD:EE:02"), calls)
        assertTrue(WatchList.admits("AA:BB:CC:DD:EE:02"))
        WatchList.hide()
        assertTrue("a closed list admits every watch", WatchList.admits("AA:BB:CC:DD:EE:01"))
    }
}
