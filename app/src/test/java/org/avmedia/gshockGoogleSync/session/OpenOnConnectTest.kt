package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who caused a connection decides whether the watch screens open: the person (the watch's connect button, a tap on
 * the card) opens them; the watch's own reconnect (kind 13/14) and a background read never leave the watch list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenOnConnectTest {
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"

    private fun connected(address: String, cause: ConnectionCause, entry: EntryReason = EntryReason.BACKGROUND) = SessionState(
        phase = SessionPhase.Ready(entry),
        button = ConnectionButton.ALWAYS_CONNECTED,
        watch = WatchSnapshot(address = address),
        cause = cause,
    )

    @After fun reset() = OpenOnConnect.cancelWaiting()

    @Test
    fun aConnectionThePersonCausedOpensTheScreens() {
        assertTrue(OpenOnConnect.shouldOpen(connected(a, ConnectionCause.USER), inFront = true, onFirstPage = true, waitingFor = null))
    }

    @Test
    fun theWatchsOwnReconnectAndABackgroundReadDoNotLeaveTheWatchList() {
        assertFalse(OpenOnConnect.shouldOpen(connected(a, ConnectionCause.WATCH), inFront = true, onFirstPage = true, waitingFor = null))
        assertFalse(OpenOnConnect.shouldOpen(connected(a, ConnectionCause.BACKGROUND_READ), true, true, waitingFor = null))
    }

    @Test
    fun aTapOnTheCardOpensWhenThatWatchConnectsHoweverItConnects() {
        assertTrue(OpenOnConnect.shouldOpen(connected(a, ConnectionCause.WATCH), true, true, waitingFor = a.lowercase()))
        assertFalse(OpenOnConnect.shouldOpen(connected(a, ConnectionCause.WATCH), true, true, waitingFor = b))
    }

    @Test
    fun aConnectionNeverMovesAwayFromAnotherScreenOrTheBackground() {
        val user = connected(a, ConnectionCause.USER)
        assertFalse(OpenOnConnect.shouldOpen(user, true, onFirstPage = false, waitingFor = a))
        assertFalse(OpenOnConnect.shouldOpen(user, inFront = false, onFirstPage = true, waitingFor = a))
        assertFalse(OpenOnConnect.shouldOpen(connected(a, ConnectionCause.USER, EntryReason.NORMAL), true, true, waitingFor = a))
        assertFalse(OpenOnConnect.shouldOpen(SessionState(), true, true, waitingFor = a))
    }

    @Test
    fun theWaitAfterATapEndsAfterTwoMinutes() = runTest {
        OpenOnConnect.waitFor(a, this)
        advanceTimeBy(OpenOnConnect.WAIT_MS - 1)
        assertEquals(a, OpenOnConnect.waiting.value)
        advanceTimeBy(2)
        assertEquals(null, OpenOnConnect.waiting.value)
    }

    @Test
    fun cancelAndConsumedClearTheWait() = runTest {
        OpenOnConnect.waitFor(a, this)
        OpenOnConnect.consumed(b)
        assertEquals(a, OpenOnConnect.waiting.value)
        OpenOnConnect.consumed(a)
        assertEquals(null, OpenOnConnect.waiting.value)
        OpenOnConnect.waitFor(a, this)
        OpenOnConnect.cancelWaiting()
        assertEquals(null, OpenOnConnect.waiting.value)
    }

    @Test
    fun theCauseIsTheKindTheLibraryReported() {
        val kind = org.avmedia.gshockapi.casio.ConnectionSetup.lastKind
        try {
            BackgroundConnection.active = false
            org.avmedia.gshockapi.casio.ConnectionSetup.lastKind = 14
            assertEquals(ConnectionCause.WATCH, currentConnectionCause())
            org.avmedia.gshockapi.casio.ConnectionSetup.lastKind = 13
            assertEquals(ConnectionCause.WATCH, currentConnectionCause())
            org.avmedia.gshockapi.casio.ConnectionSetup.lastKind = 10
            assertEquals(ConnectionCause.USER, currentConnectionCause())
            BackgroundConnection.active = true
            assertEquals(ConnectionCause.BACKGROUND_READ, currentConnectionCause())
        } finally {
            BackgroundConnection.active = false
            org.avmedia.gshockapi.casio.ConnectionSetup.lastKind = kind
        }
    }
}
