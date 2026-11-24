package org.avmedia.smartsync.homeassistant

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

    /**
     * [found]: sensor id (HaSensor.uniqueId) to entity id. Cards are made only from the sensors in it. The layout is the
     * hand-made reference: views Today, Trends and Watch. [stepGoal] is the gauge maximum (from the step goal sensor),
     * [calendars] the calendar entities of the Home Assistant (a calendar card only when there are some).
     */
    fun build(
        deviceId: String,
        title: String,
        found: Map<String, String>,
        stepGoal: Int? = null,
        calendars: List<String> = emptyList(),
    ): JsonObject {
        fun ids(vararg sensorIds: String) = sensorIds.mapNotNull { found[it] }
        fun named(vararg pairs: Pair<String, String>) = pairs.mapNotNull { (id, name) -> found[id]?.let { it to name } }

        val today = JsonArray()
        val trends = JsonArray()
        val watch = JsonArray()
        fun JsonArray.put(card: JsonObject?) { if (card != null) add(card) }

        today.put(gauge("Steps today", found["steps_today"], stepGoal ?: DEFAULT_STEP_GOAL))
        today.put(glance("Today", named("kcal_today" to "kcal", "active_minutes" to "Active", "walk_minutes" to "Walk", "run_minutes" to "Run")))
        today.put(glance("Heart rate", named("hr_rest" to "Resting", "hr_min" to "Min", "hr_max" to "Max", "hr_recent_avg" to "Recent")))
        today.put(
            glance(
                "Last night",
                named("sleep_score" to "Score", "sleep_total" to "Total", "sleep_deep" to "Deep", "sleep_light" to "Light", "sleep_rem" to "REM", "sleep_awake" to "Awake"),
            ),
        )
        today.put(history("Steps and heart rate today", ids("steps_today", "hr_recent_avg"), 24))
        if (calendars.isNotEmpty()) {
            today.put(
                JsonObject().apply {
                    addProperty("type", "calendar"); addProperty("title", "Coming up"); addProperty("initial_view", "listWeek")
                    add("entities", list(calendars))
                },
            )
        }

        trends.put(stats("Steps per day", ids("steps_today"), 30, "change", "bar"))
        trends.put(stats("Heart rate per day", ids("hr_rest", "hr_min", "hr_max"), 30, "mean", "line"))
        trends.put(stats("Energy and active minutes per day", ids("kcal_today", "active_minutes"), 30, "change", "bar"))
        trends.put(stats("Walk, run, still per day", ids("walk_minutes", "run_minutes", "still_minutes"), 14, "change", "bar"))
        trends.put(stats("Steps per week", ids("steps_today"), 90, "change", "bar", period = "week"))
        trends.put(stats("Sleep score per night", ids("sleep_score"), 30, "mean", "bar"))
        trends.put(stats("Sleep per night, minutes", ids("sleep_total", "sleep_deep", "sleep_light", "sleep_rem"), 30, "mean", "bar"))

        watch.put(
            entities(
                "Watch",
                ids(
                    "connected", "last_sync", "health_recorded_at", "battery", "temperature", "phone_battery", "home_city",
                    "home_dst", "timer_seconds", "watch_settings", "notifications_supported", "step_goal",
                ),
            ),
        )
        watch.put(history("Battery and temperature, 7 days", ids("battery", "temperature", "phone_battery"), 168))
        watch.put(history("Connection, 24 h", ids("connected"), 24))
        if (found.isNotEmpty()) watch.put(testButton(title))

        val views = JsonArray()
        listOf(Triple("Today", "today" to "mdi:walk", today), Triple("Trends", "trends" to "mdi:chart-bar", trends), Triple("Watch", "watch" to "mdi:watch", watch))
            .forEach { (name, pathIcon, cards) ->
                if (cards.size() > 0) views.add(JsonObject().apply {
                    addProperty("title", name); addProperty("path", pathIcon.first); addProperty("icon", pathIcon.second); add("cards", cards)
                })
            }
        return JsonObject().apply {
            add(MARKER_KEY, marker(deviceId, found.keys))
            addProperty("title", title)
            add("views", views)
        }
    }

    /** The gauge goes green at the goal, yellow from half of it. */
    private fun gauge(name: String, entity: String?, max: Int): JsonObject? =
        if (entity == null) null else JsonObject().apply {
            addProperty("type", "gauge"); addProperty("name", name); addProperty("entity", entity)
            addProperty("min", 0); addProperty("max", max); addProperty("needle", false)
            add("severity", JsonObject().apply { addProperty("green", max); addProperty("yellow", max / 2); addProperty("red", 0) })
        }

    private fun glance(title: String, items: List<Pair<String, String>>): JsonObject? =
        if (items.isEmpty()) null else JsonObject().apply {
            addProperty("type", "glance"); addProperty("title", title); addProperty("columns", 4)
            add("entities", JsonArray().also { a -> items.forEach { (e, n) -> a.add(JsonObject().apply { addProperty("entity", e); addProperty("name", n) }) } })
        }

    /** A button that sends a test notification to the watch through Home Assistant's mobile_app notify service. */
    private fun testButton(deviceName: String): JsonObject = JsonObject().apply {
        addProperty("type", "button"); addProperty("name", "Send a test to the watch"); addProperty("icon", "mdi:watch-vibrate")
        addProperty("show_state", false)
        add("tap_action", JsonObject().apply {
            addProperty("action", "perform-action")
            addProperty("perform_action", "notify.mobile_app_${slug(deviceName)}")
            add("data", JsonObject().apply { addProperty("title", "Test"); addProperty("message", "Hello from Home Assistant") })
            add("confirmation", JsonObject().apply { addProperty("text", "Send a test notification to the watch?") })
        })
    }

    const val DEFAULT_STEP_GOAL = 8000

    /** True when the config has at least one card (a dashboard without a sensor is not worth making). */
    fun hasCards(config: JsonObject): Boolean =
        config.getAsJsonArray("views")?.any { (it as? JsonObject)?.getAsJsonArray("cards")?.size()?.let { n -> n > 0 } == true } == true

    private fun list(items: List<String>) = JsonArray().also { a -> items.forEach(a::add) }

    private fun entities(title: String, ids: List<String>): JsonObject? =
        if (ids.isEmpty()) null else JsonObject().apply {
            addProperty("type", "entities"); addProperty("title", title); add("entities", list(ids))
        }

    private fun stats(title: String, ids: List<String>, days: Int, stat: String, chart: String, period: String = "day"): JsonObject? =
        if (ids.isEmpty()) null else JsonObject().apply {
            addProperty("type", "statistics-graph"); addProperty("title", title); add("entities", list(ids))
            addProperty("period", period); addProperty("days_to_show", days)
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
