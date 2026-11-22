package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class HaMemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

private class HaCall(val url: String, val body: String, val bearer: String?) {
    val type: String? get() = runCatching { JsonParser.parseString(body).asJsonObject.get("type").asString }.getOrNull()
}

private var haNextWebhook = 0

private fun haOk(c: HaCall) =
    if (c.url.endsWith("/registrations")) HaResponse(200, """{"webhook_id":"hook${++haNextWebhook}"}""")
    else if (c.type == "update_sensor_states") HaResponse(200, """{"battery":{"success":true}}""")
    else HaResponse(200, """{"success":true}""")

/** Answers from [answer]; the default is a good reply. */
private class HaFakeTransport(val answer: (HaCall) -> HaResponse = { haOk(it) }) : HaTransport {
    val calls = ArrayList<HaCall>()
    override fun post(url: String, body: String, bearer: String?): HaResponse {
        val c = HaCall(url, body, bearer)
        calls += c
        return answer(c)
    }
}

class HaClientTest {
    private val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")
    private val job = HaJob(mapOf("battery" to 80, "steps_today" to 1234))

    private fun connected(t: HaFakeTransport = HaFakeTransport(), store: HaStore = HaStore(HaMemoryStore())): Triple<HaClient, HaFakeTransport, HaStore> {
        haNextWebhook = 0
        val client = HaClient(t, store)
        assertTrue(client.connect("https://ha.example/", " tok ", reg) is HaResult.Ok)
        t.calls.clear()
        return Triple(client, t, store)
    }

    @Test
    fun connectRegistersWithBearerAndKeepsTheWebhookId() {
        haNextWebhook = 0
        val t = HaFakeTransport()
        val store = HaStore(HaMemoryStore())
        val r = HaClient(t, store).connect("https://ha.example/", " tok ", reg)
        assertTrue(r is HaResult.Ok)
        assertEquals("Connected as CASIO GBX-H5600", r.message)
        assertEquals("https://ha.example/api/mobile_app/registrations", t.calls.single().url)
        assertEquals("tok", t.calls.single().bearer)
        assertEquals("hook1", store.load()?.webhookId)
    }

    @Test
    fun connectShowsTheHttpErrorAndKeepsNoWebhook() {
        val store = HaStore(HaMemoryStore())
        val r = HaClient(HaFakeTransport { HaResponse(401, "") }, store).connect("https://ha.example", "bad", reg)
        assertTrue(r is HaResult.Failed)
        assertTrue(r.message.contains("401"))
        assertNull(store.load())
    }

    @Test
    fun connectRefusesAUrlWithoutScheme() {
        assertTrue(HaClient(HaFakeTransport(), HaStore(HaMemoryStore())).connect("ha.example", "t", reg) is HaResult.Failed)
    }

    @Test
    fun firstSendRegistersSensorsThenUpdatesWithoutAuthHeader() {
        val (client, t) = connected().let { it.first to it.second }
        assertTrue(client.send(job) is HaResult.Ok)
        val types = t.calls.map { it.type }
        assertEquals(HaSensors.ALL.size, types.count { it == "register_sensor" })
        assertEquals("update_sensor_states", types.last())
        assertTrue(t.calls.all { it.url == "https://ha.example/api/webhook/hook1" && it.bearer == null })
    }

    @Test
    fun secondSendDoesNotRegisterSensorsAgain() {
        val (client, t) = connected().let { it.first to it.second }
        client.send(job)
        t.calls.clear()
        client.send(job)
        assertEquals(listOf("update_sensor_states"), t.calls.map { it.type })
    }

    @Test
    fun deletedRegistrationRegistersAgainAndRetries() {
        var gone = true
        val t = HaFakeTransport { c ->
            if (c.url.endsWith("/api/webhook/hook1") && gone) { gone = false; HaResponse(410, "") } else haOk(c)
        }
        val (client, _, store) = connected(t)
        val r = client.send(job)
        assertTrue(r.message, r is HaResult.Ok)
        assertEquals("hook2", store.load()?.webhookId)
        assertEquals(1, t.calls.count { it.url.endsWith("/registrations") })
        val last = t.calls.last()
        assertEquals("https://ha.example/api/webhook/hook2", last.url)
        assertEquals("update_sensor_states", last.type)
        assertEquals(HaSensors.ALL.size, t.calls.count { it.url.endsWith("hook2") && it.type == "register_sensor" })
    }

    @Test
    fun emptyAnswerMeansDeletedRegistrationToo() {
        // The real HA answers 200 with no body for a webhook id it no longer knows (seen 2026-11-22).
        var gone = true
        val t = HaFakeTransport { c ->
            if (c.url.endsWith("/api/webhook/hook1") && gone) { gone = false; HaResponse(200, "") } else haOk(c)
        }
        val (client, _, store) = connected(t)
        assertTrue(client.send(job) is HaResult.Ok)
        assertEquals("hook2", store.load()?.webhookId)
    }

    @Test
    fun registrationGoneTwiceStopsInsteadOfLooping() {
        val t = HaFakeTransport { c -> if (c.url.contains("/api/webhook/")) HaResponse(410, "") else haOk(c) }
        val (client, _, _) = connected(t)
        val r = client.send(job)
        assertTrue(r is HaResult.Failed)
        assertEquals(1, t.calls.count { it.url.endsWith("/registrations") })
    }

    @Test
    fun sensorHaLostIsRegisteredAgain() {
        var first = true
        val t = HaFakeTransport { c ->
            if (c.type == "update_sensor_states" && first) {
                first = false
                HaResponse(200, """{"battery":{"success":false,"error":{"code":"not_registered"}}}""")
            } else haOk(c)
        }
        val (client, _, _) = connected(t)
        assertTrue(client.send(job) is HaResult.Ok)
        assertEquals(2 * HaSensors.ALL.size, t.calls.count { it.type == "register_sensor" })
        assertEquals(2, t.calls.count { it.type == "update_sensor_states" })
    }

    @Test
    fun serverErrorAndNoNetworkAreRetriedAuthErrorIsNot() {
        listOf(503 to true, null to true, 401 to false, 400 to false).forEach { (status, retry) ->
            val (client, _, _) = connected(HaFakeTransport { c -> if (c.url.contains("/api/webhook/")) HaResponse(status, "x") else haOk(c) })
            val r = client.send(job)
            assertEquals("status $status", retry, r is HaResult.Retry)
            assertFalse(r is HaResult.Ok)
        }
    }

    @Test
    fun buttonEventIsSentAfterTheStates() {
        val (client, t) = connected().let { it.first to it.second }
        client.send(HaJob(job.states, mapOf("trigger" to "watch_action")))
        val last = JsonParser.parseString(t.calls.last().body).asJsonObject
        assertEquals("fire_event", last.get("type").asString)
        assertEquals("gshock_button", last.getAsJsonObject("data").get("event_type").asString)
    }

    @Test
    fun sendWithoutConnectionFailsWithoutCalling() {
        val t = HaFakeTransport()
        assertTrue(HaClient(t, HaStore(HaMemoryStore())).send(job) is HaResult.Failed)
        assertTrue(t.calls.isEmpty())
    }

    @Test
    fun pendingJobKeepsAWaitingEventAndWholeNumbers() {
        val store = HaStore(HaMemoryStore())
        store.putPending(HaJob(mapOf("steps_today" to 1234), mapOf("trigger" to "watch_action")))
        store.putPending(HaJob(mapOf("steps_today" to 1300)))
        val p = store.getPending()!!
        assertEquals("watch_action", p.event?.get("trigger"))
        assertEquals(1300L, p.states["steps_today"])
    }

    @Test
    fun disconnectForgetsTheWebhookButKeepsUrlAndToken() {
        val (_, _, store) = connected()
        store.disconnect()
        assertNull(store.load())
        assertEquals("tok", store.loadForm().token)
        assertEquals("https://ha.example", store.loadForm().baseUrl)
    }
}
