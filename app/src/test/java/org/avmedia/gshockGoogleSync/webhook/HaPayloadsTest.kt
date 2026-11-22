package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaPayloadsTest {
    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun deviceIdIsStableForOneAddress() {
        val a = HaPayloads.deviceId("AA:BB:CC:DD:EE:FF")
        assertEquals("gshock-aabbccddeeff", a)
        assertEquals(a, HaPayloads.deviceId("aa:bb:cc:dd:ee:ff"))
        assertEquals(a, HaPayloads.deviceId("AABBCCDDEEFF"))
    }

    @Test
    fun deviceNameIsTheRealWatchNameWithCasioOnce() {
        assertEquals("CASIO GBX-H5600", HaPayloads.deviceName("GBX-H5600"))
        assertEquals("CASIO GBX-H5600", HaPayloads.deviceName("CASIO GBX-H5600"))
    }

    @Test
    fun registrationHasTheFieldsHomeAssistantNeeds() {
        val o = obj(HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1.2", "14"))
        assertEquals("org.avmedia.gshocksync.webhook", o.get("app_id").asString)
        assertEquals("G-Shock Smart Sync", o.get("app_name").asString)
        assertEquals("CASIO", o.get("manufacturer").asString)
        assertEquals("CASIO GBX-H5600", o.get("device_name").asString)
        assertEquals("Android", o.get("os_name").asString)
        assertFalse(o.get("supports_encryption").asBoolean)
    }

    @Test
    fun registerSensorCarriesUnitClassAndState() {
        val s = HaSensors.ALL.first { it.uniqueId == "battery" }
        val o = obj(HaPayloads.registerSensor(s, 80))
        assertEquals("register_sensor", o.get("type").asString)
        val d = o.getAsJsonObject("data")
        assertEquals("battery", d.get("unique_id").asString)
        assertEquals("sensor", d.get("type").asString)
        assertEquals("battery", d.get("device_class").asString)
        assertEquals("%", d.get("unit_of_measurement").asString)
        assertEquals("measurement", d.get("state_class").asString)
        assertEquals(80, d.get("state").asInt)
    }

    @Test
    fun registerSensorWithoutValueKeepsTheStateKeyAsNull() {
        val d = obj(HaPayloads.registerSensor(HaSensors.ALL.first(), null)).getAsJsonObject("data")
        assertTrue(d.has("state"))
        assertTrue(d.get("state").isJsonNull)
    }

    @Test
    fun registerSensorLeavesOutUnsetFields() {
        val s = HaSensors.ALL.first { it.uniqueId == "last_sync" }
        val d = obj(HaPayloads.registerSensor(s, "2026-11-22T10:00:00+01:00")).getAsJsonObject("data")
        assertEquals("timestamp", d.get("device_class").asString)
        assertFalse(d.has("unit_of_measurement"))
        assertFalse(d.has("state_class"))
    }

    @Test
    fun unknownValuesAreLeftOutNeverZero() {
        val states = HaSensors.states(
            mapOf("watch.battery" to 70, "watch.temperature" to null, "health.steps_today" to 0, "app.last_sync" to " ")
        )
        assertEquals(setOf("battery", "steps_today"), states.keys)
        assertEquals(0, states["steps_today"])
        val json = obj(HaPayloads.updateSensorStates(states))
        assertEquals("update_sensor_states", json.get("type").asString)
        val ids = json.getAsJsonArray("data").map { it.asJsonObject.get("unique_id").asString }
        assertEquals(listOf("battery", "steps_today"), ids.sorted())
        assertFalse(ids.contains("temperature"))
    }

    @Test
    fun everySensorReadsAVariableOfTheRegistry() {
        val names = WatchVariables.registry(fakeEnv()).names
        HaSensors.ALL.forEach { assertTrue("${it.variable} is not a variable", it.variable in names) }
        assertEquals(HaSensors.ALL.size, HaSensors.ALL.map { it.uniqueId }.toSet().size)
    }

    @Test
    fun fingerprintChangesWhenASensorChanges() {
        val a = HaSensors.fingerprint(HaSensors.ALL)
        assertEquals(a, HaSensors.fingerprint(HaSensors.ALL))
        assertTrue(a != HaSensors.fingerprint(HaSensors.ALL.dropLast(1)))
    }

    private fun fakeEnv(): WatchVariables.Env = WatchVariables.Env(
        api = io.mockk.mockk(relaxed = true),
        features = io.mockk.mockk(relaxed = true),
        raw = io.mockk.mockk(relaxed = true),
        isH5600 = { true },
        phoneBattery = { 50 },
        lastSync = { null },
    )
}
