package org.avmedia.gshockGoogleSync.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Open when the watch connects": off keeps the first page, a tap or the switch opens, per address. */
@RunWith(RobolectricTestRunner::class)
class OpenOnConnectTest {
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"

    private fun connected(address: String, entry: EntryReason = EntryReason.BACKGROUND) = SessionState(
        phase = SessionPhase.Ready(entry),
        button = ConnectionButton.ALWAYS_CONNECTED,
        watch = WatchSnapshot(address = address)
    )

    @After fun reset() = OpenOnConnect.cancelWaiting()

    @Test
    fun switchOffKeepsTheFirstPage() {
        assertFalse(OpenOnConnect.shouldOpen(connected(a), inFront = true, onFirstPage = true, switchOn = false, waitingFor = null))
    }

    @Test
    fun switchOnOpensWhenTheWatchConnectsWithTheAppInFront() {
        assertTrue(OpenOnConnect.shouldOpen(connected(a), inFront = true, onFirstPage = true, switchOn = true, waitingFor = null))
    }

    @Test
    fun tapOpensEvenWithTheSwitchOffButOnlyForThatWatch() {
        assertTrue(OpenOnConnect.shouldOpen(connected(a), true, true, switchOn = false, waitingFor = a.lowercase()))
        assertFalse(OpenOnConnect.shouldOpen(connected(a), true, true, switchOn = false, waitingFor = b))
    }

    @Test
    fun aConnectionNeverMovesAwayFromAnotherScreenOrTheBackground() {
        assertFalse(OpenOnConnect.shouldOpen(connected(a), true, onFirstPage = false, switchOn = true, waitingFor = a))
        assertFalse(OpenOnConnect.shouldOpen(connected(a), inFront = false, onFirstPage = true, switchOn = true, waitingFor = a))
        assertFalse(OpenOnConnect.shouldOpen(connected(a, EntryReason.NORMAL), true, true, switchOn = true, waitingFor = a))
        assertFalse(OpenOnConnect.shouldOpen(SessionState(), true, true, switchOn = true, waitingFor = a))
    }

    @Test
    fun cancelAndConsumedClearTheWait() {
        OpenOnConnect.waitFor(a)
        OpenOnConnect.consumed(b)
        assertEquals(a, OpenOnConnect.waiting.value)
        OpenOnConnect.consumed(a)
        assertEquals(null, OpenOnConnect.waiting.value)
        OpenOnConnect.waitFor(a)
        OpenOnConnect.cancelWaiting()
        assertEquals(null, OpenOnConnect.waiting.value)
    }

    @Test
    fun theSwitchPersistsPerAddressAndDefaultsOff() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(OpenOnConnect.isOn(context, a))
        OpenOnConnect.set(context, a, true)
        assertTrue(OpenOnConnect.isOn(context, a))
        assertTrue(OpenOnConnect.isOn(context, a.lowercase()))
        assertFalse(OpenOnConnect.isOn(context, b))
        OpenOnConnect.set(context, a, false)
        assertFalse(OpenOnConnect.isOn(context, a))
    }
}
