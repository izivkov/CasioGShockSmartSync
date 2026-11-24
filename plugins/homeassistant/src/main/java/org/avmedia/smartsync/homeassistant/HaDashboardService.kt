package org.avmedia.smartsync.homeassistant

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** What is at the dashboard address of this watch. */
sealed interface DashStatus {
    /** Nothing there (or not known yet). */
    data object None : DashStatus

    /** Made by this app; [outdated] when the sensors of the watch changed since. */
    data class Ours(val outdated: Boolean) : DashStatus

    /** Something this app did not make: it is never changed. */
    data object Foreign : DashStatus
}

/** The result of one dashboard action in plain words. [text] is the YAML for a copy, [url] the dashboard page. */
class DashResult(val ok: Boolean, val message: String, val url: String = "", val text: String = "")

/**
 * Creates, updates and describes the Home Assistant dashboard of one watch over the websocket API (admin token),
 * and writes the same dashboard as YAML for the raw editor (no admin needed). Android-free.
 */
class HaDashboardService(
    private val ws: HaWsConnector,
    private val store: HaStore,
    private val rest: HaTransport,
) {
    fun status(deviceId: String, deviceName: String): DashStatus = try {
        val cfg = store.load() ?: return DashStatus.None
        ws.connect(cfg.baseUrl, cfg.token).use { s ->
            val path = HaDashboard.urlPath(deviceId)
            if (!exists(s, path)) return DashStatus.None
            val config = s.call("lovelace/config", forPath(path)).takeIf { it.ok }?.result as? JsonObject
            if (!HaDashboard.isOurs(config, deviceId)) return DashStatus.Foreign
            val now = entities(s, deviceName)?.keys.orEmpty()
            DashStatus.Ours(outdated = now.isNotEmpty() && now != HaDashboard.markedSensors(config))
        }
    } catch (e: Exception) {
        DashStatus.None
    }

    fun createOrUpdate(deviceId: String, deviceName: String): DashResult {
        val cfg = store.load() ?: return DashResult(false, "Save the Home Assistant setup first")
        return try {
            ws.connect(cfg.baseUrl, cfg.token).use { s -> run(s, cfg, deviceId, deviceName) }
        } catch (e: HaWsException) {
            DashResult(false, if (e.authRefused) "Home Assistant refused the token" else "Could not reach Home Assistant: ${e.message}")
        }
    }

    private fun run(s: HaWsSession, cfg: HaConfig, deviceId: String, deviceName: String): DashResult {
        val path = HaDashboard.urlPath(deviceId)
        val found = entities(s, deviceName) ?: return noAdmin()
        if (found.isEmpty()) return DashResult(false, "Home Assistant has no sensors of this watch yet. Connect the watch first.")
        val states = s.call("get_states").takeIf { it.ok }?.result.objects()
        val config = HaDashboard.build(deviceId, deviceName, found, stepGoalOf(states, found["step_goal"]), calendarsOf(states))
        val list = s.call("lovelace/dashboards/list")
        if (!list.ok) return fail(list)
        val present = list.result.objects().any { it.get("url_path")?.asString == path }
        val verb: String
        if (present) {
            val old = s.call("lovelace/config", forPath(path)).takeIf { it.ok }?.result as? JsonObject
            if (!HaDashboard.isOurs(old, deviceId)) {
                return DashResult(false, "A dashboard at /$path exists that this app did not make. It was not changed.")
            }
            verb = "updated"
        } else {
            val made = s.call("lovelace/dashboards/create", JsonObject().apply {
                addProperty("url_path", path); addProperty("title", deviceName); addProperty("icon", "mdi:watch")
                addProperty("show_in_sidebar", true); addProperty("require_admin", false)
            })
            if (!made.ok) return fail(made)
            verb = "created"
        }
        val saved = s.call("lovelace/config/save", forPath(path).apply { add("config", config) })
        if (!saved.ok) return fail(saved)
        return DashResult(true, "Dashboard $verb with ${cardCount(config)} cards.", url = "${cfg.baseUrl}/$path")
    }

    /** The dashboard as YAML. Needs no admin: the entity ids come from the states HA answers to any token. */
    fun yaml(deviceId: String, deviceName: String): DashResult {
        val cfg = store.load() ?: return DashResult(false, "Save the Home Assistant setup first")
        val r = rest.get("${cfg.baseUrl}/api/states", cfg.token)
        if (r.status == null || r.status !in 200..299) return DashResult(false, "Could not read the sensors from Home Assistant")
        val have = runCatching { com.google.gson.JsonParser.parseString(r.body).asJsonArray.map { it.asJsonObject.get("entity_id").asString }.toSet() }
            .getOrDefault(emptySet())
        val found = HaDashboard.predictedEntities(deviceName).filterValues { it in have }
        if (found.isEmpty()) return DashResult(false, "Home Assistant has no sensors of this watch yet. Connect the watch first.")
        val states = runCatching { com.google.gson.JsonParser.parseString(r.body).asJsonArray.mapNotNull { it as? JsonObject } }.getOrDefault(emptyList())
        return DashResult(true, "Copied.", text = HaYaml.dump(HaDashboard.build(deviceId, deviceName, found, stepGoalOf(states, found["step_goal"]), calendarsOf(states))))
    }

    private fun stepGoalOf(states: List<JsonObject>, entity: String?): Int? =
        states.firstOrNull { it.get("entity_id")?.asString == entity }?.get("state")?.asString?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

    private fun calendarsOf(states: List<JsonObject>): List<String> =
        states.mapNotNull { it.get("entity_id")?.asString }.filter { it.startsWith("calendar.") }

    private fun cardCount(config: JsonObject): Int =
        config.getAsJsonArray("views").sumOf { (it as JsonObject).getAsJsonArray("cards").size() }

    private fun exists(s: HaWsSession, path: String): Boolean {
        val list = s.call("lovelace/dashboards/list")
        return list.ok && list.result.objects().any { it.get("url_path")?.asString == path }
    }

    private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun forPath(path: String) = JsonObject().apply { addProperty("url_path", path) }

    /** Sensor id to entity id for this watch; null when the registry is closed to this token. */
    private fun entities(s: HaWsSession, deviceName: String): Map<String, String>? {
        val devices = s.call("config/device_registry/list")
        if (!devices.ok) return null
        val id = devices.result.objects().firstOrNull { d ->
            listOf("name", "name_by_user").any { k -> d.get(k)?.takeIf { v -> v.isJsonPrimitive }?.asString.equals(deviceName.trim(), ignoreCase = true) }
        }?.get("id")?.asString ?: return emptyMap()
        val reg = s.call("config/entity_registry/list")
        if (!reg.ok) return null
        return HaDashboard.foundInRegistry(reg.result as? JsonArray ?: JsonArray(), id)
    }

    private fun noAdmin() = DashResult(false, NO_ADMIN)

    private fun fail(r: WsReply): DashResult =
        if (r.code == "unauthorized") noAdmin() else DashResult(false, "Home Assistant said: ${r.message ?: r.code ?: "error"}")

    companion object {
        const val NO_ADMIN =
            "This token has no administrator rights, so the app cannot make dashboards. Use a token of an administrator, or copy the YAML below."
    }
}
