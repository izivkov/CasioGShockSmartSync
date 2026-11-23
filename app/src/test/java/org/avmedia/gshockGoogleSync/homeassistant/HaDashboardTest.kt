package org.avmedia.gshockGoogleSync.homeassistant

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

    private fun cards(c: JsonObject) = c.getAsJsonArray("views").flatMap { it.asJsonObject.getAsJsonArray("cards").map { card -> card.asJsonObject } }

    /** The card's title, or its name (the gauge and the button have a name). */
    private fun cardTitles(c: JsonObject) = cards(c).map { (it.get("title") ?: it.get("name")).asString }

    @Test fun onlyPresentSensorsGetCards() {
        val found = mapOf("steps_today" to "sensor.w_steps_today", "battery" to "sensor.w_battery")
        val c = HaDashboard.build(id, "W", found)
        assertEquals(
            listOf(
                "Steps today", "Steps and heart rate today", "Steps per day", "Steps per week", "Watch",
                "Battery and temperature, 7 days", "Send a test to the watch",
            ),
            cardTitles(c),
        )
        val watch = cards(c).first { it.get("title")?.asString == "Watch" }.getAsJsonArray("entities").map { it.asString }
        assertEquals(listOf("sensor.w_battery"), watch)
        assertFalse(cardTitles(c).contains("Heart rate per day"))
        assertFalse(cardTitles(c).contains("Walk, run, still per day"))
        assertFalse(cardTitles(c).contains("Last night"))
    }

    @Test fun noSensorsNoCards() {
        assertFalse(HaDashboard.hasCards(HaDashboard.build(id, "W", emptyMap())))
        assertTrue(HaDashboard.hasCards(HaDashboard.build(id, "W", all)))
    }

    @Test fun fullSetHasTheReferenceViewsAndCards() {
        val c = HaDashboard.build(id, "W", all)
        assertEquals(listOf("today", "trends", "watch"), c.getAsJsonArray("views").map { it.asJsonObject.get("path").asString })
        assertEquals(
            listOf(
                "Steps today", "Today", "Heart rate", "Last night", "Steps and heart rate today",
                "Steps per day", "Heart rate per day", "Energy and active minutes per day", "Walk, run, still per day",
                "Steps per week", "Sleep score per night", "Sleep per night, minutes",
                "Watch", "Battery and temperature, 7 days", "Connection, 24 h", "Send a test to the watch",
            ),
            cardTitles(c),
        )
        val steps = cards(c).first { it.get("title")?.asString == "Steps per day" }
        assertEquals("statistics-graph", steps.get("type").asString)
        assertEquals("change", steps.getAsJsonArray("stat_types")[0].asString)
        assertEquals("day", steps.get("period").asString)
        val weekly = cards(c).first { it.get("title")?.asString == "Steps per week" }
        assertEquals("week", weekly.get("period").asString)
    }

    @Test fun theGaugeMaximumIsTheStepGoal() {
        val g = cards(HaDashboard.build(id, "W", all, stepGoal = 6000)).first { it.get("type").asString == "gauge" }
        assertEquals(6000, g.get("max").asInt)
        assertEquals(6000, g.getAsJsonObject("severity").get("green").asInt)
        assertEquals(3000, g.getAsJsonObject("severity").get("yellow").asInt)
        val d = cards(HaDashboard.build(id, "W", all)).first { it.get("type").asString == "gauge" }
        assertEquals(HaDashboard.DEFAULT_STEP_GOAL, d.get("max").asInt)
    }

    @Test fun aCalendarCardOnlyWhenCalendarsExist() {
        assertFalse(cards(HaDashboard.build(id, "W", all)).any { it.get("type").asString == "calendar" })
        val c = cards(HaDashboard.build(id, "W", all, calendars = listOf("calendar.family"))).first { it.get("type").asString == "calendar" }
        assertEquals("calendar.family", c.getAsJsonArray("entities")[0].asString)
    }

    @Test fun sleepCardsOnlyWhenSleepSensorsExist() {
        val noSleep = all.filterKeys { !it.startsWith("sleep") }
        assertFalse(cardTitles(HaDashboard.build(id, "W", noSleep)).any { it.startsWith("Sleep") || it == "Last night" })
        assertTrue(cardTitles(HaDashboard.build(id, "W", all)).containsAll(listOf("Last night", "Sleep score per night")))
    }

    @Test fun theTestButtonUsesTheNotifyServiceOfTheDevice() {
        val b = cards(HaDashboard.build(id, "CASIO GBX-H5600", all)).first { it.get("type").asString == "button" }
        assertEquals("notify.mobile_app_casio_gbx_h5600", b.getAsJsonObject("tap_action").get("perform_action").asString)
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
        assertTrue(y, y.contains("views:\n- title: Today\n  path: today\n  icon: \"mdi:walk\"\n  cards:\n  - type: gauge\n"))
        assertTrue(y, y.contains("\n    - sensor.w_steps_today\n"))
        assertTrue(y, y.contains("    stat_types:\n    - change\n"))
        assertTrue(y, y.contains("    severity:\n      green: 8000\n"))
        assertFalse(y.contains("\t"))
    }

    @Test fun socketUrlSwapsTheScheme() {
        assertEquals("wss://ha.example/api/websocket", HaOkHttpWs.socketUrl("https://ha.example/"))
        assertEquals("ws://192.168.1.5:8123/api/websocket", HaOkHttpWs.socketUrl("http://192.168.1.5:8123"))
        assertNotNull(HaOkHttpWs)
    }
}
