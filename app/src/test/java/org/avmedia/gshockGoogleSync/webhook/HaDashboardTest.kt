package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaDashboardTest {
    private val id = "gshock-c5951dde28df"
    private val all = HaDashboard.predictedEntities("CASIO GBX-H5600")

    private fun cardTitles(c: JsonObject) =
        c.getAsJsonArray("views")[0].asJsonObject.getAsJsonArray("cards").map { it.asJsonObject.get("title").asString }

    @Test fun onlyPresentSensorsGetCards() {
        val found = mapOf("steps_today" to "sensor.w_steps_today", "battery" to "sensor.w_battery")
        val c = HaDashboard.build(id, "W", found)
        assertEquals(listOf("Now", "Steps per day", "Today", "Watch battery and temperature"), cardTitles(c))
        val now = c.getAsJsonArray("views")[0].asJsonObject.getAsJsonArray("cards")[0].asJsonObject.getAsJsonArray("entities").map { it.asString }
        assertEquals(listOf("sensor.w_steps_today", "sensor.w_battery"), now)
        assertFalse(cardTitles(c).contains("Heart rate"))
        assertFalse(cardTitles(c).contains("Walk, run, still per day"))
    }

    @Test fun noSensorsNoCards() {
        assertFalse(HaDashboard.hasCards(HaDashboard.build(id, "W", emptyMap())))
        assertTrue(HaDashboard.hasCards(HaDashboard.build(id, "W", all)))
    }

    @Test fun fullSetHasTheReferenceCards() {
        val c = HaDashboard.build(id, "W", all)
        assertEquals(
            listOf("Now", "Steps per day", "Heart rate", "Energy and active minutes per day", "Walk, run, still per day",
                "Sleep score per night", "Today", "Watch battery and temperature"),
            cardTitles(c),
        )
        val steps = c.getAsJsonArray("views")[0].asJsonObject.getAsJsonArray("cards")[1].asJsonObject
        assertEquals("statistics-graph", steps.get("type").asString)
        assertEquals("change", steps.getAsJsonArray("stat_types")[0].asString)
        assertEquals("day", steps.get("period").asString)
    }

    @Test fun markerNamesTheWatchAndTheSensors() {
        val c = HaDashboard.build(id, "W", mapOf("battery" to "sensor.w_battery", "steps_today" to "sensor.w_steps_today"))
        assertTrue(HaDashboard.isOurs(c, id))
        assertFalse(HaDashboard.isOurs(c, "gshock-other"))
        assertEquals(setOf("battery", "steps_today"), HaDashboard.markedSensors(c))
    }

    @Test fun aConfigWithoutTheMarkerIsNotOurs() {
        val hand = JsonParser.parseString("""{"title":"G-Shock","views":[]}""").asJsonObject
        assertFalse(HaDashboard.isOurs(hand, id))
        assertFalse(HaDashboard.isOurs(null, id))
    }

    @Test fun urlPathHasAHyphen() {
        assertEquals("gshock-c5951dde28df", HaDashboard.urlPath("gshock-c5951dde28df"))
        assertEquals("gshock-abc", HaDashboard.urlPath("ABC"))
        assertEquals("gshock-a-b", HaDashboard.urlPath("gshock_A:b"))
    }

    @Test fun registryEntriesAreFilteredByDeviceAndSensor() {
        val reg = JsonParser.parseString(
            """[
            {"platform":"mobile_app","device_id":"D1","unique_id":"abc123_steps_today","entity_id":"sensor.w_steps_today"},
            {"platform":"mobile_app","device_id":"D1","unique_id":"abc123_connected","entity_id":"binary_sensor.w_watch_connected"},
            {"platform":"mobile_app","device_id":"D2","unique_id":"zzz_battery","entity_id":"sensor.phone_battery"},
            {"platform":"other","device_id":"D1","unique_id":"abc123_battery","entity_id":"sensor.x"},
            {"platform":"mobile_app","device_id":"D1","unique_id":"abc123_temperature","entity_id":"sensor.t","disabled_by":"user"},
            {"platform":"mobile_app","device_id":"D1","unique_id":"abc123_unknown_thing","entity_id":"sensor.u"}
            ]""",
        ).asJsonArray
        assertEquals(
            mapOf("steps_today" to "sensor.w_steps_today", "connected" to "binary_sensor.w_watch_connected"),
            HaDashboard.foundInRegistry(reg, "D1"),
        )
    }

    @Test fun predictedEntityIdsFollowHaNaming() {
        assertEquals("sensor.casio_gbx_h5600_steps_today", all["steps_today"])
        assertEquals("binary_sensor.casio_gbx_h5600_watch_connected", all["connected"])
    }

    @Test fun yamlShape() {
        val y = HaYaml.dump(HaDashboard.build(id, "W", mapOf("steps_today" to "sensor.w_steps_today", "battery" to "sensor.w_battery")))
        assertTrue(y, y.startsWith("gshock_smartsync:\n  device: gshock-c5951dde28df\n"))
        assertTrue(y, y.contains("views:\n- title: Watch\n  path: watch\n  icon: \"mdi:watch\"\n  cards:\n  - type: entities\n"))
        assertTrue(y, y.contains("\n    - sensor.w_steps_today\n"))
        assertTrue(y, y.contains("    stat_types:\n    - change\n"))
        assertFalse(y.contains("\t"))
    }

    @Test fun socketUrlSwapsTheScheme() {
        assertEquals("wss://ha.example/api/websocket", HaOkHttpWs.socketUrl("https://ha.example/"))
        assertEquals("ws://192.168.1.5:8123/api/websocket", HaOkHttpWs.socketUrl("http://192.168.1.5:8123"))
        assertNotNull(HaOkHttpWs)
    }
}
