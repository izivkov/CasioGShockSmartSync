package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The Lovelace dashboard of one watch, built from the sensors Home Assistant really has. Pure: no network.
 * The config carries a marker so the app only ever updates a dashboard it made itself.
 */
object HaDashboard {
    const val MARKER_KEY = "gshock_smartsync"
    private const val MARKER_VERSION = 1

    /** Dashboard address: the watch id ("gshock-<address>") already has the hyphen HA demands. */
    fun urlPath(deviceId: String): String =
        deviceId.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').let {
            if (it.contains('-')) it else "gshock-$it"
        }

    /** The sensor ids the dashboard was built from, in registry order, written into the marker. */
    fun marker(deviceId: String, sensors: Collection<String>): JsonObject = JsonObject().apply {
        addProperty("device", deviceId)
        addProperty("version", MARKER_VERSION)
        add("sensors", JsonArray().also { a -> sensors.sorted().forEach(a::add) })
    }

    /** True only for a config this app wrote: the marker names the same watch. */
    fun isOurs(config: JsonObject?, deviceId: String): Boolean =
        config?.getAsJsonObject(MARKER_KEY)?.get("device")?.takeIf { it.isJsonPrimitive }?.asString == deviceId

    /** The sensors the marker says the dashboard was built from. */
    fun markedSensors(config: JsonObject?): Set<String> =
        config?.getAsJsonObject(MARKER_KEY)?.getAsJsonArray("sensors")?.map { it.asString }?.toSet().orEmpty()

    /** [found]: sensor id (HaSensor.uniqueId) to entity id. Cards are made only from the sensors in it. */
    fun build(deviceId: String, title: String, found: Map<String, String>): JsonObject {
        fun ids(vararg sensorIds: String) = sensorIds.mapNotNull { found[it] }
        val cards = JsonArray()
        fun add(card: JsonObject?) { if (card != null) cards.add(card) }

        add(entities("Now", ids(
            "steps_today", "step_goal", "kcal_today", "active_minutes", "hr_rest", "hr_max", "sleep_score",
            "battery", "temperature", "connected", "last_sync",
        )))
        add(stats("Steps per day", ids("steps_today"), 30, "change", "bar"))
        add(stats("Heart rate", ids("hr_rest", "hr_max"), 30, "mean", "line"))
        add(stats("Energy and active minutes per day", ids("kcal_today", "active_minutes"), 30, "change", "bar"))
        add(stats("Walk, run, still per day", ids("walk_minutes", "run_minutes", "still_minutes"), 14, "change", "bar"))
        add(stats("Sleep score per night", ids("sleep_score"), 30, "mean", "bar"))
        add(history("Today", ids("steps_today", "hr_recent_avg"), 24))
        add(history("Watch battery and temperature", ids("battery", "temperature"), 168))

        val view = JsonObject().apply {
            addProperty("title", "Watch")
            addProperty("path", "watch")
            addProperty("icon", "mdi:watch")
            add("cards", cards)
        }
        return JsonObject().apply {
            add(MARKER_KEY, marker(deviceId, found.keys))
            addProperty("title", title)
            add("views", JsonArray().also { it.add(view) })
        }
    }

    /** True when the config has at least one card (a dashboard without a sensor is not worth making). */
    fun hasCards(config: JsonObject): Boolean =
        config.getAsJsonArray("views")?.any { (it as? JsonObject)?.getAsJsonArray("cards")?.size()?.let { n -> n > 0 } == true } == true

    private fun list(items: List<String>) = JsonArray().also { a -> items.forEach(a::add) }

    private fun entities(title: String, ids: List<String>): JsonObject? =
        if (ids.isEmpty()) null else JsonObject().apply {
            addProperty("type", "entities"); addProperty("title", title); add("entities", list(ids))
        }

    private fun stats(title: String, ids: List<String>, days: Int, stat: String, chart: String): JsonObject? =
        if (ids.isEmpty()) null else JsonObject().apply {
            addProperty("type", "statistics-graph"); addProperty("title", title); add("entities", list(ids))
            addProperty("period", "day"); addProperty("days_to_show", days)
            add("stat_types", list(listOf(stat))); addProperty("chart_type", chart)
        }

    private fun history(title: String, ids: List<String>, hours: Int): JsonObject? =
        if (ids.isEmpty()) null else JsonObject().apply {
            addProperty("type", "history-graph"); addProperty("title", title)
            addProperty("hours_to_show", hours); add("entities", list(ids))
        }

    /** Entity ids Home Assistant would give the sensors of [deviceName], when the registry cannot be read. */
    fun predictedEntities(deviceName: String, sensors: List<HaSensor> = HaSensors.ALL): Map<String, String> =
        sensors.associate { s ->
            s.uniqueId to "${s.kind}.${slug("$deviceName ${s.name}")}"
        }

    fun slug(text: String): String =
        text.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("").replace(Regex("_+"), "_").trim('_')

    /**
     * Registry entries (config/entity_registry/list) of the mobile_app sensors of [haDeviceId] as sensor id to entity id.
     * The registry unique id is "<registration hash>_<sensor id>".
     */
    fun foundInRegistry(registry: JsonArray, haDeviceId: String, sensors: List<HaSensor> = HaSensors.ALL): Map<String, String> {
        val kinds = sensors.associate { it.uniqueId to it.kind }
        val out = linkedMapOf<String, String>()
        registry.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            if (o.str("platform") != "mobile_app" || o.str("device_id") != haDeviceId || o.get("disabled_by")?.isJsonNull == false) return@forEach
            val id = o.str("unique_id")?.substringAfter('_', "") ?: return@forEach
            val entity = o.str("entity_id") ?: return@forEach
            val kind = kinds[id] ?: return@forEach
            if (entity.startsWith("$kind.")) out[id] = entity
        }
        return out
    }

    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
}
