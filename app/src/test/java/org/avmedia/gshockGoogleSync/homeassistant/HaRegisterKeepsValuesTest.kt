package org.avmedia.gshockGoogleSync.homeassistant

import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MapStore : KeyValueStore {
    val map = ConcurrentHashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

private class Recorder : HaTransport {
    val bodies = mutableListOf<String>()
    override fun get(url: String, bearer: String?) = HaResponse(200, """{"message":"API running."}""")
    override fun post(url: String, body: String, bearer: String?): HaResponse {
        if (url.endsWith("/registrations")) return HaResponse(200, """{"webhook_id":"hook"}""")
        bodies += body
        return HaResponse(200, if (body.contains("update_sensor_states")) """{"battery":{"success":true}}""" else """{"success":true}""")
    }
}

/** Build 3 re-registered every sensor with state null and HA turned all health values to unknown. */
class HaRegisterKeepsValuesTest {
    private val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")

    @Test
    fun reRegisteringAfterARestartKeepsTheLastSentValues() {
        val store = HaStore(MapStore())
        val t = Recorder()
        assertTrue(HaClient(t, store).saveSettings("https://ha.example", "tok") is HaResult.Ok)
        val first = HaJob(mapOf("battery" to 80), deviceId = "gshock-1", registration = reg)
        assertTrue(HaClient(t, store).send(first) is HaResult.Ok)
        store.saveSent(first.states, 1L) // the worker does this after a good send
        // The sensor set changes (new build): the stored hash no longer matches. No fresh values this time.
        val old = store.loadRegistration("gshock-1")!!
        store.saveRegistration("gshock-1", old.copy(sensorsHash = "old"))
        t.bodies.clear()
        assertTrue(HaClient(t, store).send(HaJob(emptyMap(), deviceId = "gshock-1", registration = reg)) is HaResult.Ok)
        val battery = t.bodies.first { it.contains("register_sensor") && it.contains("\"unique_id\":\"battery\"") }
        assertTrue(battery, battery.contains("\"state\":80"))
        val never = t.bodies.first { it.contains("register_sensor") && !it.contains("\"unique_id\":\"battery\"") }
        assertTrue(never, never.contains("\"state\":null"))
        assertEquals(0, t.bodies.count { it.contains("update_sensor_states") })
    }
}
