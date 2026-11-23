package org.avmedia.gshockGoogleSync.services

import org.avmedia.gshockGoogleSync.session.ConnectionButton
import org.avmedia.gshockGoogleSync.session.SessionState
import org.avmedia.gshockGoogleSync.session.WatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The inactivity timeout goes back to the main screen; an always-connected watch keeps its link (regression of eb5c8add). */
class InactivityTimeoutTest {
    private fun run(keepsLink: Boolean): List<String> {
        val calls = mutableListOf<String>()
        InactivityTimeout.run(keepsLink, goHome = { calls += "home" }, disconnect = { calls += "disconnect" }, tellDisconnected = { calls += "tell" })
        return calls
    }

    @Test
    fun anAlwaysConnectedWatchGoesBackToTheMainScreenAndKeepsItsLink() {
        assertEquals(listOf("home"), run(keepsLink = true))
    }

    @Test
    fun anyOtherWatchGoesBackToTheMainScreenAndIsDisconnected() {
        val calls = run(keepsLink = false)
        assertTrue("home" in calls)
        assertTrue("disconnect" in calls)
        assertTrue("tell" in calls)
    }

    @Test
    fun anH5600KeepsItsLinkWhateverButtonStartedIt() {
        assertTrue(SessionState(button = ConnectionButton.NORMAL, watch = WatchSnapshot(name = "CASIO DW-H5600")).keepsLink)
        assertTrue(SessionState(button = ConnectionButton.ALWAYS_CONNECTED).keepsLink)
        assertFalse(SessionState(button = ConnectionButton.NORMAL, watch = WatchSnapshot(name = "GW-B5600")).keepsLink)
    }
}
