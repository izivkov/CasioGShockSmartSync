package org.avmedia.gshockGoogleSync.session

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Watch A keeps connected in background, the person taps another watch B. A is let go once and
 * waits while B is connected or waited for; after B's session A may reconnect by itself; a tap on A brings it back.
 */
class OtherWatchChosenTest {
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"
    private val h5600 = "CASIO GBX-H5600"
    private var connected: String? = null
    private val calls = mutableListOf<String>()
    private val switch = WatchSwitch(
        connectedAddress = { connected },
        disconnectCurrent = { calls += "disconnect $connected"; connected = null },
        connect = { calls += "connect $it" },
    )

    private fun session(address: String, phase: SessionPhase = SessionPhase.Ready(EntryReason.NORMAL)) =
        SessionState(phase = phase, watch = WatchSnapshot(address = address))

    @Before fun setUp() {
        KeepConnected.resetForTest(a)
        OpenOnConnect.cancelWaiting()
    }

    @After fun tearDown() {
        KeepConnected.resetForTest()
        OpenOnConnect.cancelWaiting()
    }

    @Test
    fun step1TappingBLetsAGoOnceAndWaitsForB() {
        connected = a
        switch.tap(b)
        assertEquals(listOf("disconnect $a"), calls)
        assertEquals(b, KeepConnected.pausedFor.value)
        switch.tap(b) // a second tap while nothing is connected lets nothing go
        assertEquals(1, calls.size)
    }

    @Test
    fun step2WhileBIsConnectedOrWaitedForTheReconnectsOfAAreIgnoredAndItsCardShowsWaiting() {
        connected = a
        switch.tap(b)
        KeepConnected.updatePause(SessionState(), waitingFor = b) // still waiting for B
        assertFalse(KeepConnected.admits(h5600, a, background = false, waitingFor = b))
        assertFalse("not in a background window either", KeepConnected.admits(h5600, a, background = true, waitingFor = b))
        KeepConnected.updatePause(session(b), waitingFor = null) // B connected, the wait used up
        assertFalse(KeepConnected.admits(h5600, a, background = false, waitingFor = null))
        assertTrue("B itself connects", KeepConnected.admits("CASIO GW-B5600", b, background = false, waitingFor = b))
        assertEquals(WatchStatus.WAITING, WatchStatus.of(a, session(b), KeepConnected.holder.value))
    }

    @Test
    fun step3AfterBsSessionEndsAReconnectsByItselfAgain() {
        for (end in listOf("closed", "cancel", "timeout")) {
            connected = a
            switch.tap(b)
            when (end) {
                // B disconnected or the inactivity timeout ended it: its session is closed.
                "closed" -> KeepConnected.updatePause(session(b, SessionPhase.Closed("Watch disconnected")), waitingFor = null)
                // Cancel, or the two minutes ran out, while B never came.
                else -> KeepConnected.updatePause(SessionState(), waitingFor = null)
            }
            assertNull(end, KeepConnected.pausedFor.value)
            assertTrue(end, KeepConnected.admits(h5600, a, background = false, waitingFor = null))
        }
    }

    @Test
    fun theTwoMinuteWaitRunningOutResumesA() = kotlinx.coroutines.test.runTest {
        connected = a
        switch.tap(b)
        OpenOnConnect.waitFor(b, this)
        KeepConnected.updatePause(SessionState(), OpenOnConnect.waiting.value)
        assertEquals(b, KeepConnected.pausedFor.value)
        testScheduler.advanceTimeBy(OpenOnConnect.WAIT_MS + 1)
        KeepConnected.updatePause(SessionState(), OpenOnConnect.waiting.value)
        assertNull(KeepConnected.pausedFor.value)
    }

    @Test
    fun step4TappingAWhileBIsConnectedLetsBGoAndAConnects() {
        connected = a
        switch.tap(b)
        assertEquals(b, KeepConnected.pausedFor.value)
        connected = b
        calls.clear()
        switch.choose(a) // from the watch list
        assertEquals(listOf("disconnect $b", "connect $a"), calls)
        assertNull(KeepConnected.pausedFor.value)
        assertTrue(KeepConnected.admits(h5600, a, background = false, waitingFor = null))
    }

    @Test
    fun theKnownGapAClosedListNoLongerLetsATakeTheLinkBackFromB() {
        WatchList.show()
        connected = a
        switch.choose(b)
        WatchList.hide() // the list closes while B is connected
        KeepConnected.updatePause(session(b), waitingFor = null)
        assertTrue(WatchList.admits(a))
        assertFalse(KeepConnected.admits(h5600, a, background = false, waitingFor = null))
    }
}
