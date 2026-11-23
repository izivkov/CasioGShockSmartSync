package org.avmedia.gshockGoogleSync.homeassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaCatchUpTest {
    private val store = HaStore(HaMemoryStore()).also {
        it.save(HaConfig("https://ha.example", "tok"))
        it.saveRegistration("gshock-1", HaRegistration("hook1", "{}"))
    }
    private val sent = ArrayList<Pair<String, String>>()
    private var answer = HaResponse(200, """{"t":"unknown"}""")
    private val applied = ArrayList<Map<String, Any?>>()
    private val logs = ArrayList<String>()
    private val catchUp = HaCatchUp(
        transport = { url, body, _ -> sent += url to body; answer },
        store = store,
        applySettings = { applied += it },
        log = { logs += it },
        clock = { "t$it" },
    )

    private fun helper(json: String) { answer = HaResponse(200, """{"t":${com.google.gson.Gson().toJson(json)}}""") }

    @Test fun readsTheHelperThroughTheWebhookTemplate() {
        catchUp.run("gshock-1", null, 0)
        assertEquals("https://ha.example/api/webhook/hook1", sent.single().first)
        assertTrue(sent.single().second.contains("render_template"))
        assertTrue(sent.single().second.contains("input_text.gshock_settings"))
    }

    @Test fun aChangedValueIsAppliedOnceAndTheSameValueIsNotAppliedAgain() {
        helper("""{"step_goal":9000,"watch_face":"steps"}""")
        catchUp.run("gshock-1", null, 0)
        catchUp.run("gshock-1", 1, 2)
        assertEquals(1, applied.size)
        assertEquals(9000.0, applied[0]["step_goal"])
        assertEquals("steps", applied[0]["watch_face"])
        helper("""{"step_goal":8000}""")
        catchUp.run("gshock-1", null, 0)
        assertEquals(2, applied.size)
        assertEquals(8000.0, applied[1]["step_goal"])
    }

    @Test fun aMissingHelperDoesNothingAndSaysNothing() {
        for (v in listOf("unknown", "unavailable", "")) {
            helper(v)
            catchUp.run("gshock-1", null, 0)
        }
        assertTrue(applied.isEmpty())
        assertTrue(logs.isEmpty())
    }

    @Test fun anUnreachableHaOrAnErrorDoesNothing() {
        answer = HaResponse(null, "timeout"); catchUp.run("gshock-1", null, 0)
        answer = HaResponse(500, "x"); catchUp.run("gshock-1", null, 0)
        answer = HaResponse(200, "not json"); catchUp.run("gshock-1", null, 0)
        assertTrue(applied.isEmpty())
        assertTrue(logs.isEmpty())
    }

    @Test fun textThatIsNotAJsonObjectIsLoggedOnce() {
        helper("not json")
        catchUp.run("gshock-1", null, 0)
        catchUp.run("gshock-1", null, 0)
        assertTrue(applied.isEmpty())
        assertEquals(1, logs.size)
    }

    @Test fun anOutageLogsTheGapOnce() {
        catchUp.run("gshock-1", 1000, 13000)
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("t1000") && logs[0].contains("t13000"))
    }

    @Test fun withoutARegistrationNothingIsSent() {
        catchUp.run("other", null, 0)
        assertTrue(sent.isEmpty())
    }
}
