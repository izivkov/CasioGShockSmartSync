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
private class HaFakeTransport(val answer: (HaCall) -> HaResponse = { haOk(it) }, val get: HaResponse = HaResponse(200, """{"message":"API running."}""")) : HaTransport {
    val calls = ArrayList<HaCall>()
    val gets = ArrayList<String>()
    override fun get(url: String, bearer: String?): HaResponse { gets += url; return get }
    override fun post(url: String, body: String, bearer: String?): HaResponse {
        val c = HaCall(url, body, bearer)
        calls += c
        return answer(c)
    }
}

class HaClientTest {
    private val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")
    private val job = HaJob(mapOf("battery" to 80, "steps_today" to 1234), deviceId = "gshock-1", registration = reg)

    private fun connected(t: HaFakeTransport = HaFakeTransport(), store: HaStore = HaStore(HaMemoryStore()), setupOnly: Boolean = false): Triple<HaClient, HaFakeTransport, HaStore> {
        haNextWebhook = 0
        val client = HaClient(t, store)
        assertTrue(client.saveSettings("https://ha.example/", " tok ") is HaResult.Ok)
        if (!setupOnly) assertTrue(client.ensureRegistered("gshock-1", reg) is HaResult.Ok)
        t.calls.clear()
        return Triple(client, t, store)
    }

    @Test
    fun saveChecksTheApiThenKeepsUrlAndTokenWithoutRegistering() {
        val t = HaFakeTransport()
        val store = HaStore(HaMemoryStore())
        val r = HaClient(t, store).saveSettings("https://ha.example/", " tok ", " My watch ", "GBX-H5600-1")
        assertTrue(r.message, r is HaResult.Ok)
        assertEquals("https://ha.example/api/", t.gets.single())
        assertTrue(t.calls.isEmpty())
        assertEquals(HaConfig("https://ha.example", "tok", "My watch", "GBX-H5600-1"), store.load())
    }

    @Test
    fun saveShowsTheHttpErrorAndSavesNothing() {
        val store = HaStore(HaMemoryStore())
        val r = HaClient(HaFakeTransport(get = HaResponse(401, "")), store).saveSettings("https://ha.example", "bad")
        assertTrue(r is HaResult.Failed)
        assertTrue(r.message.contains("401"))
        assertNull(store.load())
    }

    @Test
    fun saveRefusesAUrlWithoutSchemeAndAnEmptyToken() {
        val c = HaClient(HaFakeTransport(), HaStore(HaMemoryStore()))
        assertTrue(c.saveSettings("ha.example", "t") is HaResult.Failed)
        assertTrue(c.saveSettings("https://ha.example", " ") is HaResult.Failed)
    }

    @Test
    fun aWatchIsRegisteredOnceAndNeverAgainForTheSameAddress() {
        val (client, t, store) = connected(setupOnly = true)
        assertTrue(client.send(job) is HaResult.Ok)
        assertEquals(1, t.calls.count { it.url.endsWith("/registrations") })
        client.send(job)
        client.send(job)
        assertEquals(1, t.calls.count { it.url.endsWith("/registrations") })
        assertEquals("hook1", store.loadRegistration("gshock-1")?.webhookId)
    }

    @Test
    fun aSecondWatchAddressGetsItsOwnRegistration() {
        val (client, t, store) = connected(setupOnly = true)
        client.send(job)
        val reg2 = HaPayloads.registration("gshock-2", "CASIO GW-B5600", "GW-B5600", "1", "14")
        assertTrue(client.send(HaJob(job.states, deviceId = "gshock-2", registration = reg2)) is HaResult.Ok)
        assertEquals(2, t.calls.count { it.url.endsWith("/registrations") })
        assertEquals("hook1", store.loadRegistration("gshock-1")?.webhookId)
        assertEquals("hook2", store.loadRegistration("gshock-2")?.webhookId)
        assertEquals(listOf("gshock-1", "gshock-2"), store.registeredIds())
    }

    @Test
    fun registeringIsRefusedWithoutSetupAndWithoutAWatch() {
        val t = HaFakeTransport()
        assertTrue(HaClient(t, HaStore(HaMemoryStore())).send(job) is HaResult.Failed)
        val (client, _, _) = connected(setupOnly = true)
        assertTrue(client.send(HaJob(job.states)) is HaResult.Failed)
        assertTrue(t.calls.none { it.url.endsWith("/registrations") })
    }

    @Test
    fun changedNameAndModelUpdateTheExistingDeviceWithoutRegisteringAgain() {
        val (client, t, store) = connected()
        val body = HaPayloads.registration("gshock-1", "My watch", "GBX-H5600-1", "1", "14")
        val r = client.send(job.copy(registration = body))
        assertTrue(r.message, r is HaResult.Ok)
        val c = t.calls.first()
        assertEquals("https://ha.example/api/webhook/hook1", c.url)
        assertEquals("update_registration", c.type)
        val d = JsonParser.parseString(c.body).asJsonObject.getAsJsonObject("data")
        assertEquals("My watch", d.get("device_name").asString)
        assertEquals("GBX-H5600-1", d.get("model").asString)
        assertEquals(0, t.calls.count { it.url.endsWith("/registrations") })
        val saved = store.loadRegistration("gshock-1")!!
        assertEquals("hook1", saved.webhookId)
        assertEquals(body, saved.registration)
    }

    @Test
    fun deletedRegistrationIsMadeAgainWithTheNewNameAndModel() {
        val t = HaFakeTransport { c ->
            if (c.url.endsWith("/api/webhook/hook1")) HaResponse(200, "") else haOk(c)
        }
        val (client, _, store) = connected(t)
        val body = HaPayloads.registration("gshock-1", "My watch", "GBX-H5600-1", "1", "14")
        assertTrue(client.send(job.copy(registration = body)) is HaResult.Ok)
        val reg = t.calls.last { it.url.endsWith("/registrations") }
        assertEquals("GBX-H5600-1", JsonParser.parseString(reg.body).asJsonObject.get("model").asString)
        assertEquals("hook2", store.loadRegistration("gshock-1")?.webhookId)
    }

    @Test
    fun updateRegistrationFailureIsShownAndKeepsTheWebhook() {
        val t = HaFakeTransport { c -> if (c.type == "update_registration") HaResponse(400, "x") else haOk(c) }
        val (client, _, store) = connected(t)
        val r = client.send(job.copy(registration = HaPayloads.registration("gshock-1", "A", "B", "1", "14")))
        assertTrue(r is HaResult.Failed)
        assertEquals("hook1", store.loadRegistration("gshock-1")?.webhookId)
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
        assertEquals("hook2", store.loadRegistration("gshock-1")?.webhookId)
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
            if (c.url.endsWith("/api/webhook/hook1") && c.type == "get_config") HaResponse(200, "")
            else if (c.url.endsWith("/api/webhook/hook1") && gone) { gone = false; HaResponse(200, "") } else haOk(c)
        }
        val (client, _, store) = connected(t)
        assertTrue(client.send(job) is HaResult.Ok)
        assertEquals("hook2", store.loadRegistration("gshock-1")?.webhookId)
    }

    @Test
    fun emptyAnswerToALiveCommandDoesNotRegisterAgain() {
        // A live registration may answer a command with nothing; get_config still answers JSON.
        val t = HaFakeTransport { c ->
            if (c.type == "fire_event") HaResponse(200, "")
            else if (c.type == "get_config") HaResponse(200, """{"version":"2026.1"}""")
            else haOk(c)
        }
        val (client, _, store) = connected(t)
        val r = client.send(job.copy(event = mapOf("trigger" to "watch_action")))
        assertTrue(r.message, r is HaResult.Ok)
        assertEquals(0, t.calls.count { it.url.endsWith("/registrations") })
        assertEquals("hook1", store.loadRegistration("gshock-1")?.webhookId)
    }

    @Test
    fun emptyAnswerToUpdateRegistrationDoesNotRegisterAgain() {
        val t = HaFakeTransport { c ->
            if (c.type == "update_registration") HaResponse(200, "")
            else if (c.type == "get_config") HaResponse(200, """{"version":"2026.1"}""")
            else haOk(c)
        }
        val (client, _, store) = connected(t)
        assertTrue(client.send(job.copy(registration = HaPayloads.registration("gshock-1", "A", "B", "1", "14"))) is HaResult.Ok)
        assertEquals(0, t.calls.count { it.url.endsWith("/registrations") })
        assertEquals("hook1", store.loadRegistration("gshock-1")?.webhookId)
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
        client.send(job.copy(event = mapOf("trigger" to "watch_action")))
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
    fun forgetClearsTheSetupAndEveryRegistration() {
        val (client, _, store) = connected()
        client.send(job.copy(deviceId = "gshock-2", registration = HaPayloads.registration("gshock-2", "B", "B", "1", "14")))
        store.forgetAll()
        assertNull(store.load())
        assertNull(store.loadRegistration("gshock-1"))
        assertNull(store.loadRegistration("gshock-2"))
        assertTrue(store.registeredIds().isEmpty())
    }
}
