package org.avmedia.smartsync.homeassistant

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watch registers only after the owner confirmed its name on the HA screen, and with that name. */
class HaNameFirstTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }

    private class T : HaTransport {
        val posts = ArrayList<Pair<String, String>>()
        override fun get(url: String, bearer: String?) = HaResponse(200, "{}")
        override fun post(url: String, body: String, bearer: String?): HaResponse {
            posts += url to body
            return if (url.endsWith("/registrations")) HaResponse(200, """{"webhook_id":"hook1"}""") else HaResponse(200, """{"success":true}""")
        }
    }

    private val address = "AA:BB:CC:DD:EE:FF"
    private val id = HaPayloads.deviceId(address)
    private val ble = "CASIO GBX-H5600"

    private fun store() = HaStore(Mem()).apply { save(HaConfig("https://ha.example", "tok")) }
    private fun client(t: T, s: HaStore) = HaClient(t, s, mayRegister = { s.confirmedName(it) != null })
    private fun nameIn(body: String) = JsonParser.parseString(body).asJsonObject.get("device_name").asString

    @Test fun nothingIsRegisteredBeforeTheNameIsConfirmed() {
        val s = store()
        val t = T()
        assertNull(HaJobs.registration(s, address, ble, s.load()!!, "1", "14"))
        val body = HaPayloads.registrationFor(address, ble, s.load()!!, "1", "14")
        val r = client(t, s).send(HaJob(mapOf("battery" to 80), deviceId = id, registration = body))
        assertTrue(r is HaResult.Failed && r.message == HaClient.NAME_FIRST)
        assertTrue(t.posts.isEmpty())
        assertNull(s.loadRegistration(id))
    }

    @Test fun theRegistrationUsesTheConfirmedName() {
        val s = store()
        assertFalse(s.confirmName(id, "  "))
        assertTrue(s.confirmName(id, " GBX-H5600KI-5ER "))
        val body = HaJobs.registration(s, address, ble, s.load()!!, "1", "14")!!
        assertEquals("GBX-H5600KI-5ER", nameIn(body))
        val t = T()
        assertTrue(client(t, s).send(HaJob(mapOf("battery" to 80), deviceId = id, registration = body)) is HaResult.Ok)
        val reg = t.posts.single { it.first.endsWith("/registrations") }
        assertEquals("GBX-H5600KI-5ER", nameIn(reg.second))
    }

    @Test fun anExistingRegistrationIsUntouched() {
        val s = store()
        val old = HaPayloads.registrationFor(address, ble, s.load()!!, "1", "14")
        s.saveRegistration(id, HaRegistration("hook1", old))
        val body = HaJobs.registration(s, address, ble, s.load()!!, "1", "14")!!
        assertEquals(nameIn(old), nameIn(body))
        val t = T()
        assertTrue(client(t, s).send(HaJob(mapOf("battery" to 80), deviceId = id, registration = body)) is HaResult.Ok)
        assertTrue(t.posts.none { it.first.endsWith("/registrations") })
    }
}
