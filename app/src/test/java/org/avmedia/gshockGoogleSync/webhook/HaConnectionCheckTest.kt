package org.avmedia.gshockGoogleSync.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaConnectionCheckTest {
    private fun check(status: Int?, body: String = "") = HaConnectionCheck.classify(HaResponse(status, body))

    @Test fun apiRunningIsOk() = assertEquals(HaCheck.OK, check(200, """{"message":"API running."}"""))

    @Test fun refusedTokenIsWrongToken() {
        assertEquals(HaCheck.WRONG_TOKEN, check(401))
        assertEquals(HaCheck.WRONG_TOKEN, check(403))
    }

    @Test fun noAnswerCannotReach() {
        assertEquals(HaCheck.CANNOT_REACH, check(null, "Unable to resolve host \"ha.example\""))
        assertEquals(HaCheck.CANNOT_REACH, check(null, "timeout"))
        assertEquals(HaCheck.CANNOT_REACH, check(502))
        assertEquals(HaCheck.CANNOT_REACH, check(503))
    }

    @Test fun anotherServerIsNotHomeAssistant() {
        assertEquals(HaCheck.NOT_HOME_ASSISTANT, check(200, "<html><body>Welcome</body></html>"))
        assertEquals(HaCheck.NOT_HOME_ASSISTANT, check(200, ""))
        assertEquals(HaCheck.NOT_HOME_ASSISTANT, check(404, "Not found"))
    }

    @Test fun blockedPlainHttpHasItsOwnAnswer() =
        assertEquals(HaCheck.CLEARTEXT_BLOCKED, check(null, "Cleartext HTTP traffic to ha.local not permitted"))

    @Test fun everyMessageIsPlainWords() {
        HaCheck.entries.forEach {
            val m = HaConnectionCheck.message(it)
            assertTrue("$it has a message", m.length > 10)
            assertFalse("$it: $m", Regex("HTTP|POST|payload|JSON|\\d{3}").containsMatchIn(m))
        }
        assertTrue(HaConnectionCheck.message(HaCheck.WRONG_TOKEN).contains("token"))
        assertTrue(HaConnectionCheck.message(HaCheck.CANNOT_REACH).contains("reach"))
        assertTrue(HaConnectionCheck.message(HaCheck.NOT_HOME_ASSISTANT).contains("not Home Assistant"))
    }

    @Test fun saveGivesTheMessageAndKeepsTheCodeForDetails() {
        val store = HaStore(HaMemoryStore())
        val t = object : HaTransport {
            override fun post(url: String, body: String, bearer: String?) = HaResponse(200, "")
            override fun get(url: String, bearer: String?) = HaResponse(401, "")
        }
        val r = HaClient(t, store).saveSettings("https://ha.example", "bad")
        assertTrue(r is HaResult.Failed)
        assertEquals(HaConnectionCheck.message(HaCheck.WRONG_TOKEN), r.message)
        assertTrue(r.detail.contains("401"))
        assertNull(store.load())
    }
}
