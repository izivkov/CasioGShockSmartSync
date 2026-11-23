package org.avmedia.gshockapi

import CachedIO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockapi.casio.CommandTable
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.io.ButtonPressedIO
import org.avmedia.gshockapi.io.IO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The init's button read on a reconnect: late or already taken by the connection setup, it must not raise an Error. */
class InitButtonTest {
    /** Kind 14 (the watch connected itself after a link loss), 19 bytes. */
    private val kind14 = "0x10 17 62 07 38 85 CD 7F 0E 03 0F FF FF FF FF 24 00 00 00"
    private val kind1 = "0x10 17 62 07 38 85 CD 7F 01 03 0F FF FF FF FF 24 00 00 00"

    @Before fun clean() {
        CachedIO.clearCache()
        ButtonPressedIO.forgetAnswer()
        ConnectionSetup.reset()
        ConnectionSetup.busy = { true } // the kind is noted, no setup writes start
    }

    @After fun restore() {
        ConnectionSetup.busy = { org.avmedia.gshockapi.io.H5600HealthIO.isActive || org.avmedia.gshockapi.io.H5600WorkoutIO.isActive }
        ConnectionSetup.reset()
        ButtonPressedIO.forgetAnswer()
        CachedIO.clearCache()
    }

    @Test fun theAnswerTheSetupAlreadyGotIsSharedNotReadAgain() {
        ButtonPressedIO.onReceived(kind14)
        val button = runBlocking { withTimeout(1_500) { ButtonPressedIO.request() } }
        assertEquals(IO.WatchButton.ALLAYS_CONNECTED_CONNECTION, button)
    }

    @Test fun initWaitsAsLongAsTheQueueDoes() {
        assertEquals(CommandTable.REQUEST_TIMEOUT_MS, InitCheck.BUTTON_TIMEOUT_MS)
        assertTrue(InitCheck.BUTTON_TIMEOUT_MS > 3_000L)
    }

    @Test fun theKindOfTheLinkTellsAnAutomaticReconnect() {
        assertFalse(ConnectionSetup.isAutomaticConnect())
        ConnectionSetup.onKind(kind14)
        assertTrue(ConnectionSetup.isAutomaticConnect())
        ConnectionSetup.reset()
        assertFalse(ConnectionSetup.isAutomaticConnect())
        ConnectionSetup.onKind(kind1)
        assertFalse(ConnectionSetup.isAutomaticConnect())
    }

    @Test fun noAnswerOnAnAutomaticReconnectCompletesInitWithOneLogLineAndNoError() {
        val d = InitCheck.decide(null, automatic = true)
        assertTrue(d.complete)
        assertNull(d.error)
        assertNotNull(d.log)
        val invalid = InitCheck.decide(IO.WatchButton.INVALID, automatic = true)
        assertTrue(invalid.complete && invalid.error == null && invalid.log != null)
    }

    @Test fun noAnswerAfterTheUserStartedTheConnectionIsABlockingError() {
        val d = InitCheck.decide(null, automatic = false)
        assertFalse(d.complete)
        assertEquals("Timed out waiting for button info from the watch", d.error)
        assertEquals("Received invalid button info from the watch", InitCheck.decide(IO.WatchButton.INVALID, false).error)
    }

    @Test fun anAnswerCompletesInitQuietly() {
        val d = InitCheck.decide(IO.WatchButton.LOWER_LEFT, automatic = false)
        assertTrue(d.complete && d.error == null && d.log == null)
    }

    /**
     * The watch can answer nothing on many links in a row (no kind answer, no 0x10), and each
     * one raised the blocking "Timed out waiting for button info" popup. Without a kind answer nobody knows a button was
     * pressed: init ends quietly with one log line, the link is released and connecting goes on.
     */
    @Test fun aLinkWithoutAKindAnswerEndsQuietly() {
        val d = InitCheck.decide(null, automatic = false, kindKnown = false)
        assertFalse(d.complete)
        assertNull(d.error)
        assertNotNull(d.log)
        // A known kind keeps the rule: a user-started connection that failed is a blocking error.
        assertEquals("Timed out waiting for button info from the watch", InitCheck.decide(null, automatic = false, kindKnown = true).error)
    }
}
