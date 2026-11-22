package org.avmedia.gshockGoogleSync.webhook

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One Home Assistant sensor, fed by one variable of the [VariableRegistry]. Adding a sensor is
 * adding one row to [HaSensors.ALL]; there is no second reader of watch values.
 */
data class HaSensor(
    val uniqueId: String,
    val name: String,
    /** Name of the variable in the registry that holds the value. */
    val variable: String,
    val deviceClass: String? = null,
    val unit: String? = null,
    val stateClass: String? = null,
    val icon: String? = null,
    val entityCategory: String? = null,
    /** "sensor" or "binary_sensor" (a Boolean state). */
    val kind: String = "sensor",
    val convert: Convert = Convert.NONE,
    /** HA attribute name to the registry variable that fills it. */
    val attributes: Map<String, String> = emptyMap(),
)

/** What happens to a registry value before it becomes the state. Data, not a lambda: the fingerprint prints it. */
enum class Convert {
    NONE,

    /** The watch counts seconds, the sensor is in minutes (whole minutes, like health.active_minutes). */
    SECONDS_TO_MINUTES,

    /** A local date-time without offset gets the phone's offset: HA refuses a timestamp without one. */
    LOCAL_TO_OFFSET,

    /** Home Assistant refuses a state longer than 255 characters. */
    TRUNCATE_250,
}

object HaSensors {
    /** Every `watch.settings.*` value except the time format (that one is the state). */
    private val SETTINGS_ATTRIBUTES: Map<String, String> = listOf(
        "date_format", "language", "auto_light", "light_duration", "power_saving", "button_tone", "key_vibration",
        "hourly_chime", "do_not_disturb", "font", "time_adjustment", "adjustment_minutes", "fine_adjustment",
    ).associateWith { "watch.settings.$it" }

    val ALL: List<HaSensor> = listOf(
        HaSensor("battery", "Battery", "watch.battery", "battery", "%", "measurement", "mdi:battery", "diagnostic"),
        HaSensor("temperature", "Temperature", "watch.temperature", "temperature", "°C", "measurement", "mdi:thermometer"),
        HaSensor("steps_today", "Steps today", "health.steps_today", null, "steps", "total_increasing", "mdi:shoe-print", attributes = mapOf("steps_by_hour" to "health.steps_by_hour")),
        HaSensor("kcal_today", "Energy today", "health.kcal_today", null, "kcal", "total_increasing", "mdi:fire"),
        HaSensor("active_minutes", "Active minutes", "health.active_minutes", "duration", "min", "total_increasing", "mdi:run"),
        HaSensor("hr_rest", "Resting heart rate", "health.hr_rest", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("hr_max", "Max heart rate", "health.hr_max", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("phone_battery", "Phone battery", "phone.battery", "battery", "%", "measurement", "mdi:cellphone", "diagnostic"),
        HaSensor("last_sync", "Last sync", "app.last_sync", "timestamp", null, null, "mdi:sync", "diagnostic"),
        HaSensor("step_goal", "Step goal", "health.step_goal", null, "steps", null, "mdi:flag-checkered"),
        HaSensor("hr_min", "Min heart rate", "health.hr_min", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("hr_recent_avg", "Recent heart rate", "health.hr_recent_avg", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("walk_minutes", "Walking today", "health.walk_seconds", "duration", "min", "total_increasing", "mdi:walk", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("run_minutes", "Running today", "health.run_seconds", "duration", "min", "total_increasing", "mdi:run-fast", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("still_minutes", "Still today", "health.still_seconds", "duration", "min", "total_increasing", "mdi:seat", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("health_recorded_at", "Health data recorded", "health.recorded_at", "timestamp", null, null, "mdi:clock-check", "diagnostic", convert = Convert.LOCAL_TO_OFFSET),
        HaSensor("connected", "Watch connected", "watch.connected", "connectivity", null, null, "mdi:watch-vibrate", "diagnostic", kind = "binary_sensor"),
        HaSensor("home_city", "Home city", "watch.home_city", null, null, null, "mdi:city"),
        HaSensor("home_dst", "Home daylight saving", "watch.dst.home", null, null, null, "mdi:weather-sunny", kind = "binary_sensor"),
        HaSensor("timer_seconds", "Watch timer", "watch.timer_seconds", "duration", "s", null, "mdi:timer-outline"),
        HaSensor("notifications_supported", "Notifications supported", "watch.notifications_supported", null, null, null, "mdi:bell-ring", "diagnostic", kind = "binary_sensor"),
        HaSensor("watch_firmware", "Watch firmware", "watch.firmware", null, null, null, "mdi:chip", "diagnostic", attributes = mapOf("version_raw" to "watch.firmware_raw")),
        HaSensor("watch_face_raw", "Watch face (raw)", "watch.face_raw", null, null, null, "mdi:watch", "diagnostic", convert = Convert.TRUNCATE_250),
        HaSensor("face_items_raw", "Face items (raw)", "watch.face_items_raw", null, null, null, "mdi:watch", "diagnostic", convert = Convert.TRUNCATE_250),
        HaSensor(
            "watch_settings_raw", "Watch settings (raw)", "watch.raw_settings_read_at", "timestamp", null, null, "mdi:code-braces", "diagnostic",
            attributes = RawSettings.CODES.associate { RawSettings.name(it) to RawSettings.variable(it) },
        ),
        HaSensor("watch_settings", "Watch settings", "watch.settings.time_format", null, null, null, "mdi:cog", "diagnostic", attributes = SETTINGS_ATTRIBUTES),
    )

    /** The variables to resolve for an update: the states and the attributes. */
    val VARIABLES: Set<String> = variablesOf(ALL)

    fun variablesOf(sensors: List<HaSensor>): Set<String> = sensors.flatMap { listOf(it.variable) + it.attributes.values }.toSet()

    private fun usable(v: Any?): Any? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is Boolean, is Number -> v
        else -> null
    }

    private fun convert(s: HaSensor, v: Any, zone: ZoneId): Any? = when (s.convert) {
        Convert.NONE -> v
        Convert.SECONDS_TO_MINUTES -> (v as? Number)?.let { it.toLong() / 60 }
        Convert.TRUNCATE_250 -> (v as? String)?.take(250)
        Convert.LOCAL_TO_OFFSET -> (v as? String)?.let {
            runCatching { LocalDateTime.parse(it).atZone(zone).toOffsetDateTime().toString() }.getOrNull()
        }
    }

    /** Registry values to sensor states. A null or blank value is left out, never sent as 0. */
    fun states(
        values: Map<String, Any?>,
        sensors: List<HaSensor> = ALL,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, Any> =
        sensors.mapNotNull { s ->
            usable(values[s.variable])?.let { convert(s, it, zone) }?.let { s.uniqueId to it }
        }.toMap()

    /** Attribute maps by sensor id, for sensors that have attributes with a value. Unknown values are left out. */
    fun attributes(values: Map<String, Any?>, sensors: List<HaSensor> = ALL): Map<String, Map<String, Any?>> =
        sensors.mapNotNull { s ->
            val a = s.attributes.mapNotNull { (name, variable) ->
                val v = values[variable]
                val ok = if (v is List<*>) v.takeIf { it.isNotEmpty() } else usable(v)
                ok?.let { name to it }
            }.toMap()
            if (a.isEmpty()) null else s.uniqueId to a
        }.toMap()

    /** Changes whenever a definition changes: sensors are registered again then, not on every send. */
    fun fingerprint(sensors: List<HaSensor> = ALL): String {
        val text = sensors.joinToString("|") { it.toString() }
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    }
}
