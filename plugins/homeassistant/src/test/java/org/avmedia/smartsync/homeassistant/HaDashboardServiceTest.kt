package org.avmedia.smartsync.homeassistant

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class DashMemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

/** A fake Home Assistant: answers by call type and records every call. */
private class FakeWs(var existing: JsonObject? = null, var admin: Boolean = true, var path: String = "gshock-c5951dde28df") : HaWsConnector {
    val calls = ArrayList<Pair<String, JsonObject>>()
    var connectError: HaWsException? = null
    private fun arr(s: String) = JsonParser.parseString(s)

    override fun connect(baseUrl: String, token: String): HaWsSession {
        connectError?.let { throw it }
        return object : HaWsSession {
            override fun call(type: String, payload: JsonObject): WsReply {
                calls += type to payload
                if (!admin) return WsReply(false, code = "unauthorized", message = "Unauthorized")
                return when (type) {
                    "config/device_registry/list" -> WsReply(true, arr("""[{"id":"D1","name":"CASIO GBX-H5600","name_by_user":null}]"""))
                    "config/entity_registry/list" -> WsReply(true, arr(
                        """[{"platform":"mobile_app","device_id":"D1","unique_id":"h_steps_today","entity_id":"sensor.casio_steps_today"},
                           {"platform":"mobile_app","device_id":"D1","unique_id":"h_battery","entity_id":"sensor.casio_battery"}]""",
                    ))
                    "lovelace/dashboards/list" -> WsReply(true, arr(if (existing != null) """[{"url_path":"$path"}]""" else "[]"))
                    "lovelace/config" -> WsReply(existing != null, existing)
                    else -> WsReply(true, null)
                }
            }
            override fun close() {}
        }
    }

    fun types() = calls.map { it.first }
}

class HaDashboardServiceTest {
    private val id = "gshock-c5951dde28df"
    private val name = "CASIO GBX-H5600"
    private val store = HaStore(DashMemoryStore()).also { it.save(HaConfig("https://ha.example", "tok")) }
    private val noRest = HaTransport { _, _, _ -> HaResponse(200, "") }

    private fun service(ws: FakeWs) = HaDashboardService(ws, store, noRest)
    private val handMade = JsonParser.parseString("""{"title":"G-Shock","views":[{"cards":[]}]}""").asJsonObject

    @Test fun createsANewDashboardWithTheMarker() {
        val ws = FakeWs()
        val r = service(ws).createOrUpdate(id, name)
        assertTrue(r.message, r.ok)
        assertEquals("https://ha.example/$id", r.url)
        val create = ws.calls.first { it.first == "lovelace/dashboards/create" }.second
        assertEquals(id, create.get("url_path").asString)
        val save = ws.calls.first { it.first == "lovelace/config/save" }.second
        assertTrue(HaDashboard.isOurs(save.getAsJsonObject("config"), id))
        assertEquals(setOf("steps_today", "battery"), HaDashboard.markedSensors(save.getAsJsonObject("config")))
    }

    @Test fun updatesADashboardItMade() {
        val ws = FakeWs(existing = HaDashboard.build(id, name, mapOf("battery" to "sensor.casio_battery")))
        val r = service(ws).createOrUpdate(id, name)
        assertTrue(r.message, r.ok)
        assertFalse(ws.types().contains("lovelace/dashboards/create"))
        assertTrue(ws.types().contains("lovelace/config/save"))
    }

    @Test fun refusesADashboardWithoutTheMarker() {
        val ws = FakeWs(existing = handMade)
        val r = service(ws).createOrUpdate(id, name)
        assertFalse(r.ok)
        assertTrue(r.message, r.message.contains("did not make"))
        assertFalse(ws.types().contains("lovelace/config/save"))
        assertFalse(ws.types().contains("lovelace/dashboards/create"))
        assertFalse(ws.types().contains("lovelace/dashboards/delete"))
    }

    @Test fun aTokenWithoutAdminRightsGetsAClearError() {
        val ws = FakeWs(admin = false)
        val r = service(ws).createOrUpdate(id, name)
        assertFalse(r.ok)
        assertEquals(HaDashboardService.NO_ADMIN, r.message)
        assertFalse(ws.types().contains("lovelace/config/save"))
    }

    @Test fun noSensorsYetMakesNothing() {
        val ws = FakeWs()
        val empty = HaDashboardService(object : HaWsConnector {
            override fun connect(baseUrl: String, token: String) = ws.connect(baseUrl, token).let { s ->
                object : HaWsSession {
                    override fun call(type: String, payload: JsonObject) =
                        if (type == "config/entity_registry/list") WsReply(true, JsonParser.parseString("[]")) else s.call(type, payload)
                    override fun close() {}
                }
            }
        }, store, noRest)
        val r = empty.createOrUpdate(id, name)
        assertFalse(r.ok)
        assertFalse(ws.types().contains("lovelace/config/save"))
    }

    @Test fun unreachableAndRefusedTokenAreTold() {
        val ws = FakeWs().also { it.connectError = HaWsException("Home Assistant refused the token", authRefused = true) }
        assertEquals("Home Assistant refused the token", service(ws).createOrUpdate(id, name).message)
        ws.connectError = HaWsException("timeout")
        assertTrue(service(ws).createOrUpdate(id, name).message.startsWith("Could not reach"))
    }

    @Test fun statusTellsNoneOursOutdatedAndForeign() {
        assertEquals(DashStatus.None, service(FakeWs()).status(id, name))
        assertEquals(DashStatus.Foreign, service(FakeWs(existing = handMade)).status(id, name))
        val current = HaDashboard.build(id, name, mapOf("steps_today" to "sensor.casio_steps_today", "battery" to "sensor.casio_battery"))
        assertEquals(DashStatus.Ours(false), service(FakeWs(existing = current)).status(id, name))
        val older = HaDashboard.build(id, name, mapOf("battery" to "sensor.casio_battery"))
        assertEquals(DashStatus.Ours(true), service(FakeWs(existing = older)).status(id, name))
    }

    @Test fun yamlNeedsNoAdminAndUsesOnlyExistingEntities() {
        val states = """[{"entity_id":"sensor.casio_gbx_h5600_steps_today","state":"1"},{"entity_id":"light.kitchen","state":"on"}]"""
        val rest = object : HaTransport {
            override fun post(url: String, body: String, bearer: String?) = HaResponse(200, "")
            override fun get(url: String, bearer: String?) = HaResponse(200, states)
        }
        val r = HaDashboardService(FakeWs(admin = false), store, rest).yaml(id, name)
        assertTrue(r.message, r.ok)
        assertTrue(r.text, r.text.contains("sensor.casio_gbx_h5600_steps_today"))
        assertFalse(r.text.contains("battery"))
        assertTrue(r.text.startsWith("gshock_smartsync:"))
    }
}
