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
        store.saveSent("gshock-1", first.states, 1L) // the worker does this after a good send
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

    /**
     * Before: the first push after installing a build with a changed sensor set (no LifeLog read yet in
     * the new app session) re-registered steps_today with the last sent value 3184 but no attributes, and HA dropped
     * `steps_by_hour` (register_sensor replaces the attributes like an update does). Same for the heart rate curve.
     */
    @Test
    fun reRegisteringWithTheLastSentValueKeepsItsAttributes() {
        val store = HaStore(MapStore())
        val t = Recorder()
        assertTrue(HaClient(t, store).saveSettings("https://ha.example", "tok") is HaResult.Ok)
        val hours = List(24) { if (it in 13..16) 100 + it else null }
        val first = HaJob(
            mapOf("steps_today" to 3184, "hr_recent_avg" to 72),
            attributes = mapOf("steps_today" to mapOf("steps_by_hour" to hours), "hr_recent_avg" to mapOf("hr_curve_max" to 131)),
            deviceId = "gshock-1", registration = reg, h5600 = true,
        )
        assertTrue(HaClient(t, store).send(first) is HaResult.Ok)
        store.saveSent(first, 1L) // the worker does this after a good send
        // A later send of other values only must not forget them.
        store.saveSent(HaJob(mapOf("battery" to 80), deviceId = "gshock-1", registration = reg), 2L)
        val old = store.loadRegistration("gshock-1")!!
        store.saveRegistration("gshock-1", old.copy(sensorsHash = "old"))
        t.bodies.clear()
        assertTrue(HaClient(t, store).send(HaJob(mapOf("battery" to 79), deviceId = "gshock-1", registration = reg, h5600 = true)) is HaResult.Ok)
        val steps = t.bodies.first { it.contains("register_sensor") && it.contains("\"unique_id\":\"steps_today\"") }
        assertTrue(steps, steps.contains("\"state\":3184"))
        assertTrue(steps, steps.contains("\"steps_by_hour\":[null,null,null,null,null,null,null,null,null,null,null,null,null,113,114,115,116,null"))
        val hr = t.bodies.first { it.contains("register_sensor") && it.contains("\"unique_id\":\"hr_recent_avg\"") }
        assertTrue(hr, hr.contains("\"hr_curve_max\":131"))
    }
}
