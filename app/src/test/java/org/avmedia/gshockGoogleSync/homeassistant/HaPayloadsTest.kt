package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watchvalues.RawSettings
import org.avmedia.gshockGoogleSync.watchvalues.WatchDecoders
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
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
    fun emptyNameAndModelFallBackToTheWatchName() {
        assertEquals("CASIO GBX-H5600" to "GBX-H5600", HaPayloads.resolveNames("CASIO GBX-H5600", "", "  "))
        assertEquals("CASIO GBX-H5600" to "GBX-H5600", HaPayloads.resolveNames("GBX-H5600", "", ""))
        assertEquals("My watch" to "GBX-H5600-1", HaPayloads.resolveNames("CASIO GBX-H5600", " My watch ", "GBX-H5600-1"))
    }

    @Test
    fun updateRegistrationCarriesTheNewNameAndModelAndOnlyChangeableFields() {
        val body = HaPayloads.registration("gshock-1", "My watch", "GBX-H5600-1", "1.2", "14")
        val o = obj(HaPayloads.updateRegistration(body))
        assertEquals("update_registration", o.get("type").asString)
        val d = o.getAsJsonObject("data")
        assertEquals("My watch", d.get("device_name").asString)
        assertEquals("GBX-H5600-1", d.get("model").asString)
        assertEquals("1.2", d.get("app_version").asString)
        assertEquals("14", d.get("os_version").asString)
        assertEquals("CASIO", d.get("manufacturer").asString)
        assertFalse(d.has("device_id"))
        assertFalse(d.has("supports_encryption"))
    }

    @Test
    fun firmwareIsNotGuessedFromTheOwnersVersionAnswer() {
        val answer = "0x20 23 07 00 12 00 00 00 00 00 00 00 00 04 02 01 00 09 00 00"
        assertEquals("23 07 00 12 00 00 00 00 00 00 00 00 04 02 01 00 09 00 00", WatchDecoders.versionRaw(answer))
        assertEquals("unknown", WatchDecoders.firmwareState(answer))
        assertEquals(null, WatchDecoders.versionRaw("0x28 06 1D"))
        assertEquals(null, WatchDecoders.versionRaw(null))
        assertEquals(null, WatchDecoders.firmwareState("0x20"))
    }

    @Test
    fun firmwareSensorCarriesTheRawBytesAsAttribute() {
        val values = mapOf("watch.firmware" to "unknown", "watch.firmware_raw" to "23 07 00")
        assertEquals("unknown", HaSensors.states(values)["watch_firmware"])
        assertEquals("23 07 00", HaSensors.attributes(values).getValue("watch_firmware")["version_raw"])
        assertTrue(HaSensors.states(emptyMap()).isEmpty())
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

    private fun sensor(id: String) = HaSensors.ALL.first { it.uniqueId == id }

    @Test
    fun binarySensorIsRegisteredAndUpdatedAsBinarySensorWithABoolean() {
        val reg = obj(HaPayloads.registerSensor(sensor("connected"), true)).getAsJsonObject("data")
        assertEquals("binary_sensor", reg.get("type").asString)
        assertEquals("connectivity", reg.get("device_class").asString)
        assertTrue(reg.get("state").isJsonPrimitive && reg.get("state").asJsonPrimitive.isBoolean)
        assertFalse(reg.has("unit_of_measurement"))
        val states = HaSensors.states(mapOf("watch.connected" to false, "watch.dst.home" to true))
        val upd = obj(HaPayloads.updateSensorStates(states)).getAsJsonArray("data")
        val byId = upd.associate { it.asJsonObject.get("unique_id").asString to it.asJsonObject }
        assertEquals("binary_sensor", byId.getValue("connected").get("type").asString)
        assertFalse(byId.getValue("connected").get("state").asBoolean)
        assertEquals("binary_sensor", byId.getValue("home_dst").get("type").asString)
    }

    @Test
    fun stepsTodayCarriesStepsByHourAsAnAttribute() {
        val values = mapOf("health.steps_today" to 4000, "health.steps_by_hour" to listOf(0, 120, null, 300))
        val attrs = HaSensors.attributes(values)
        val upd = obj(HaPayloads.updateSensorStates(HaSensors.states(values), HaSensors.ALL, attrs))
            .getAsJsonArray("data").map { it.asJsonObject }.first { it.get("unique_id").asString == "steps_today" }
        val hours = upd.getAsJsonObject("attributes").getAsJsonArray("steps_by_hour")
        assertEquals(4, hours.size())
        assertEquals(120, hours[1].asInt)
        assertTrue(hours[2].isJsonNull)
        val reg = obj(HaPayloads.registerSensor(sensor("steps_today"), 4000, attrs["steps_today"])).getAsJsonObject("data")
        assertEquals(4, reg.getAsJsonObject("attributes").getAsJsonArray("steps_by_hour").size())
    }

    @Test
    fun watchSettingsSensorHasTheTimeFormatAsStateAndTheRestAsAttributes() {
        val values = mapOf("watch.settings.time_format" to "24h", "watch.settings.font" to "Classic", "watch.settings.auto_light" to true,
            "watch.settings.language" to " ")
        assertEquals("24h", HaSensors.states(values)["watch_settings"])
        val a = HaSensors.attributes(values).getValue("watch_settings")
        assertEquals(mapOf("font" to "Classic", "auto_light" to true), a)
    }

    @Test
    fun secondsBecomeMinutes() {
        val states = HaSensors.states(mapOf("health.walk_seconds" to 3659, "health.run_seconds" to 59, "health.still_seconds" to 7200))
        assertEquals(60L, states["walk_minutes"])
        assertEquals(0L, states["run_minutes"])
        assertEquals(120L, states["still_minutes"])
        assertEquals("min", sensor("walk_minutes").unit)
    }

    @Test
    fun faceAnswersAreKeptAsHexWithoutThe0xPrefixTheGuardDrops() {
        assertEquals("51 01 00 FF", WatchDecoders.rawAnswer("0x51 01 00 ff", 0x51))
        assertEquals("52 0A", WatchDecoders.rawAnswer("0x52 0A", 0x52))
        assertFalse(WatchDecoders.looksLikeRawHex(WatchDecoders.rawAnswer("0x51 01 00 ff", 0x51)!!))
        // wrong code, no data bytes, not hex, nothing: unknown, never a guess
        assertEquals(null, WatchDecoders.rawAnswer("0x52 01", 0x51))
        assertEquals(null, WatchDecoders.rawAnswer("0x51", 0x51))
        assertEquals(null, WatchDecoders.rawAnswer("0x51 zz", 0x51))
        assertEquals(null, WatchDecoders.rawAnswer(null, 0x51))
    }

    @Test
    fun faceSensorsAreDiagnosticAndTheirStateIsCutTo250Characters() {
        val face = sensor("watch_face_raw")
        assertEquals("watch.face_raw", face.variable)
        assertEquals("diagnostic", face.entityCategory)
        assertEquals("diagnostic", sensor("face_items_raw").entityCategory)
        assertEquals("watch.face_items_raw", sensor("face_items_raw").variable)
        val long = (1..200).joinToString(" ") { "AB" } // 599 characters
        assertEquals(250, (HaSensors.states(mapOf("watch.face_raw" to long))["watch_face_raw"] as String).length)
        assertEquals("51 01", HaSensors.states(mapOf("watch.face_raw" to "51 01"))["watch_face_raw"])
        assertFalse(HaSensors.states(mapOf("watch.face_raw" to null)).containsKey("watch_face_raw"))
    }

    @Test
    fun rawSettingsSensorShowsTheReadTimeAndHasOneAttributePerCode() {
        val s = sensor("watch_settings_raw")
        assertEquals("diagnostic", s.entityCategory)
        assertEquals("timestamp", s.deviceClass)
        assertEquals("watch.raw_settings_read_at", s.variable)
        assertEquals(RawSettings.CODES.map { RawSettings.name(it) }.toSet(), s.attributes.keys)
        assertEquals("watch.raw.0x41", s.attributes.getValue("0x41"))
        val values = mapOf("watch.raw_settings_read_at" to "2026-11-22T10:15:00+01:00", "watch.raw.0x41" to "41 01 02")
        assertEquals("2026-11-22T10:15:00+01:00", HaSensors.states(values)["watch_settings_raw"])
        assertEquals(mapOf("0x41" to "41 01 02"), HaSensors.attributes(values)["watch_settings_raw"])
    }

    @Test
    fun distanceTodayIsASensorInMetres() {
        val d = sensor("distance_today")
        assertEquals("health.distance_m", d.variable)
        assertEquals("distance", d.deviceClass)
        assertEquals("m", d.unit)
        assertEquals("total_increasing", d.stateClass)
        assertEquals(3873, HaSensors.states(mapOf("health.distance_m" to 3873))["distance_today"])
        assertFalse(HaSensors.states(mapOf("health.distance_m" to null)).containsKey("distance_today"))
    }

    @Test
    fun recordedAtGetsTheOffsetHomeAssistantNeeds() {
        val z = java.time.ZoneId.of("Europe/Brussels")
        val s = HaSensors.states(mapOf("health.recorded_at" to "2026-11-22T10:15:00"), zone = z)
        assertEquals("2026-11-22T10:15+01:00", s["health_recorded_at"])
        assertFalse(HaSensors.states(mapOf("health.recorded_at" to "garbage"), zone = z).containsKey("health_recorded_at"))
    }

    @Test
    fun newSensorsLeaveUnknownValuesOut() {
        val values = mapOf<String, Any?>(
            "health.step_goal" to null, "health.hr_min" to null, "health.walk_seconds" to null,
            "watch.notifications_supported" to null, "watch.home_city" to " ", "watch.settings.time_format" to null,
            "health.steps_by_hour" to emptyList<Int>(),
        )
        assertTrue(HaSensors.states(values).isEmpty())
        assertTrue(HaSensors.attributes(values).isEmpty())
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
