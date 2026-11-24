package org.avmedia.smartsync.homeassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner 2026-11-24: "Share health data with Home Assistant" off disables the health sensors in HA and sends no health value. */
class HaHealthSwitchTest {
    private class Recorder : HaTransport {
        val bodies = mutableListOf<String>()
        override fun get(url: String, bearer: String?) = HaResponse(200, """{"message":"API running."}""")
        override fun post(url: String, body: String, bearer: String?): HaResponse {
            if (url.endsWith("/registrations")) return HaResponse(200, """{"webhook_id":"hook"}""")
            bodies += body
            return HaResponse(200, if (body.contains("update_sensor_states")) """{"battery":{"success":true}}""" else """{"success":true}""")
        }
    }

    private val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")
    private val health = HaSensors.ALL.filter { HaGroups.of(it) == HaGroup.HEALTH }.map { it.uniqueId }.toSet()

    private fun registered(body: String) = Regex(""""unique_id":"([^"]+)"""").find(body)!!.groupValues[1]

    @Test fun offRemovesTheHealthSensorsAndOnBringsThemBack() {
        val store = HaStore(HaMemoryStore())
        val t = Recorder()
        val client = HaClient(t, store)
        assertTrue(client.saveSettings("https://ha.example", "tok") is HaResult.Ok)
        val job = HaJob(mapOf("battery" to 80, "steps_today" to 3184), deviceId = "gshock-1", registration = reg, h5600 = true)
        assertTrue(client.send(job) is HaResult.Ok)
        assertTrue("on: health sensors registered", t.bodies.any { it.contains("register_sensor") && registered(it) in health && !it.contains("\"disabled\"") })

        store.setHealthOn(false)
        t.bodies.clear()
        assertTrue(client.send(job) is HaResult.Ok)
        val disabled = t.bodies.filter { it.contains("register_sensor") && it.contains("\"disabled\":true") }.map(::registered).toSet()
        assertTrue("every health sensor of this watch is disabled", disabled.isNotEmpty() && health.containsAll(disabled))
        assertTrue("steps_today" in disabled)
        assertFalse("no other sensor is disabled", disabled.contains("battery"))
        val update = t.bodies.single { it.contains("update_sensor_states") }
        assertFalse("no health value is sent", update.contains("steps_today"))
        assertTrue(update.contains("battery"))

        t.bodies.clear()
        assertTrue(client.send(job) is HaResult.Ok)
        assertTrue("off stays off: no second registration", t.bodies.none { it.contains("register_sensor") })

        store.setHealthOn(true)
        t.bodies.clear()
        assertTrue(client.send(job) is HaResult.Ok)
        assertTrue("on again: enabled again", t.bodies.any { it.contains("register_sensor") && registered(it) == "steps_today" && it.contains("\"disabled\":false") })
        assertTrue("and its value is sent", t.bodies.single { it.contains("update_sensor_states") }.contains("steps_today"))
        t.bodies.clear()
        assertTrue(client.send(job) is HaResult.Ok)
        assertTrue("the person's own disabling is never undone later", t.bodies.none { it.contains("\"disabled\"") })
    }
}
