package org.avmedia.gshockGoogleSync.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestResultTextTest {
    @Test fun successIsPlain() {
        val r = TestResultText.of(200, "ok")
        assertTrue(r.ok)
        assertEquals("It worked. The server accepted it.", r.headline)
        assertTrue(r.details.contains("HTTP 200"))
        assertTrue(r.details.contains("ok"))
        assertFalse(r.headline.contains("200"))
    }

    @Test fun statusesInWords() {
        assertEquals("The server did not accept the login or token.", TestResultText.of(401, "").headline)
        assertEquals("The server did not accept the login or token.", TestResultText.of(403, "").headline)
        assertEquals("The server does not know this address.", TestResultText.of(404, "").headline)
        assertEquals("The server refused it. Check the address and the body.", TestResultText.of(422, "").headline)
        assertEquals("The server had a problem. Try again later.", TestResultText.of(503, "").headline)
        assertFalse(TestResultText.of(404, "").ok)
    }

    @Test fun noAnswer() {
        val r = TestResultText.of(null, "UnknownHostException: x")
        assertFalse(r.ok)
        assertEquals("No answer. Is the address right and the phone online?", r.headline)
        assertTrue(r.details.contains("No response"))
        assertTrue(r.details.contains("UnknownHostException"))
    }

    @Test fun notesAreWordsAndOnlyWhenNeeded() {
        assertTrue(TestResultText.of(200, "").notes.isEmpty())
        val r = TestResultText.of(200, "", unknown = listOf("foo"), failed = listOf("a (x)"), missing = listOf("b"), watchConnected = false)
        assertEquals(4, r.notes.size)
        assertTrue(r.notes.any { it.contains("foo") })
        assertTrue(r.notes.any { it.contains("watch is not connected", ignoreCase = true) })
    }
}
