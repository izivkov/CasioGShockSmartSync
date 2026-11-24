package org.avmedia.smartsync.homeassistant

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

    /**
     * What the host has, for the sensors that list it: the raw setting codes ("0x51", in the host's order) and the
     * settings Home Assistant may change. Set when the plugin starts ([HostKeys.from]).
     */
    data class HostKeys(val rawCodes: List<String> = emptyList(), val writableSettings: List<String> = emptyList()) {
        companion object {
            fun from(valueKeys: List<String>, settingKeys: List<String>) =
                HostKeys(valueKeys.filter { it.startsWith("watch.raw.") }.map { it.removePrefix("watch.raw.") }, settingKeys)
        }
    }

    @Volatile var hostKeys: HostKeys = HostKeys()
        set(value) { field = value; built = null }

    @Volatile private var built: Pair<HostKeys, List<HaSensor>>? = null

    /** Every sensor, for the [hostKeys] of now. */
    val ALL: List<HaSensor>
        get() = built?.takeIf { it.first == hostKeys }?.second ?: build(hostKeys).also { built = hostKeys to it }

    private fun build(keys: HostKeys): List<HaSensor> = listOf(
        HaSensor("battery", "Battery", "watch.battery", "battery", "%", "measurement", "mdi:battery", "diagnostic"),
        HaSensor("temperature", "Temperature", "watch.temperature", "temperature", "°C", "measurement", "mdi:thermometer"),
        HaSensor("steps_today", "Steps today", "health.steps_today", null, "steps", "total_increasing", "mdi:shoe-print", attributes = mapOf("steps_by_hour" to "health.steps_by_hour")),
        HaSensor("kcal_today", "Energy today", "health.kcal_today", null, "kcal", "total_increasing", "mdi:fire"),
        HaSensor("energy_total", "Total energy today (incl. resting)", "health.energy_total_kcal", null, "kcal", "total_increasing", "mdi:fire"),
        HaSensor("energy_active", "Active energy today", "health.energy_active_kcal", null, "kcal", "total_increasing", "mdi:fire-circle"),
        HaSensor("distance_today", "Distance today", "health.distance_m", "distance", "m", "total_increasing", "mdi:map-marker-distance"),
        HaSensor("active_minutes", "Active minutes", "health.active_minutes", "duration", "min", "total_increasing", "mdi:run"),
        HaSensor("hr_rest", "Resting heart rate", "health.hr_rest", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("hr_max", "Max heart rate", "health.hr_max", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("phone_battery", "Phone battery", "phone.battery", "battery", "%", "measurement", "mdi:cellphone", "diagnostic"),
        HaSensor("last_sync", "Last sync", "app.last_sync", "timestamp", null, null, "mdi:sync", "diagnostic"),
        HaSensor("step_goal", "Step goal", "health.step_goal", null, "steps", null, "mdi:flag-checkered"),
        HaSensor("hr_min", "Min heart rate", "health.hr_min", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor(
            "hr_recent_avg", "Recent heart rate", "health.hr_recent_avg", null, "bpm", "measurement", "mdi:heart-pulse",
            attributes = mapOf(
                "hr_by_slot" to "health.hr_by_slot", "hr_max_at" to "health.hr_max_at", "hr_min_at" to "health.hr_min_at",
                "hr_curve_max" to "health.hr_curve_max", "hr_curve_min" to "health.hr_curve_min",
            ),
        ),
        HaSensor("walk_minutes", "Walking today", "health.walk_seconds", "duration", "min", "total_increasing", "mdi:walk", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("run_minutes", "Running today", "health.run_seconds", "duration", "min", "total_increasing", "mdi:run-fast", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("still_minutes", "Still today", "health.still_seconds", "duration", "min", "total_increasing", "mdi:seat", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("health_recorded_at", "Health data recorded", "health.recorded_at", "timestamp", null, null, "mdi:clock-check", "diagnostic", convert = Convert.LOCAL_TO_OFFSET),
        HaSensor("ha_push_connected", "Home Assistant push connected", "app.ha_push_connected", "connectivity", null, null, "mdi:lan-connect", "diagnostic", kind = "binary_sensor", attributes = mapOf("last_change" to "app.ha_push_changed_at", "heartbeat" to "app.ha_push_heartbeat")),
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
            attributes = keys.rawCodes.associateWith { "watch.raw.$it" },
        ),
        // Sleep and night (LifeLog 0x53, 0x5A). The time base is not yet checked on a real night: names say so.
        HaSensor("sleep_score", "Sleep score", "health.sleep_score", null, null, "measurement", "mdi:sleep", attributes = mapOf("night" to "health.sleep_date")),
        HaSensor("recharge_status", "Nightly Recharge status", "health.recharge_status", null, null, "measurement", "mdi:battery-heart-variant", attributes = mapOf("level" to "health.recharge_level")),
        HaSensor("sleep_fell_asleep", "Fell asleep (unconfirmed)", "health.sleep_fell_asleep", "timestamp", null, null, "mdi:bed-clock", convert = Convert.LOCAL_TO_OFFSET),
        HaSensor("sleep_woke_up", "Woke up (unconfirmed)", "health.sleep_woke_up", "timestamp", null, null, "mdi:alarm", convert = Convert.LOCAL_TO_OFFSET),
        HaSensor("sleep_total", "Sleep total (stages unconfirmed)", "health.sleep_total_min", "duration", "min", "measurement", "mdi:bed", attributes = mapOf("stages" to "health.sleep_stages")),
        HaSensor("sleep_deep", "Deep sleep", "health.sleep_deep_min", "duration", "min", "measurement", "mdi:sleep"),
        HaSensor("sleep_light", "Light sleep", "health.sleep_light_min", "duration", "min", "measurement", "mdi:sleep"),
        HaSensor("sleep_rem", "REM sleep", "health.sleep_rem_min", "duration", "min", "measurement", "mdi:sleep"),
        HaSensor("sleep_awake", "Sleep interruptions", "health.sleep_awake_min", "duration", "min", "measurement", "mdi:sleep-off"),
        HaSensor("sleep_hr", "Heart rate in the night", "health.sleep_hr", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("sleep_breathing", "Breathing rate in the night", "health.sleep_breathing", null, "br/min", "measurement", "mdi:lungs"),
        HaSensor("spo2", "Blood oxygen", "health.spo2", null, "%", "measurement", "mdi:water-percent", attributes = mapOf("measured_at" to "health.spo2_at")),
        HaSensor(
            "cardio_load", "Cardio load ratio", "health.cardio_load_ratio", null, null, "measurement", "mdi:heart-flash",
            attributes = mapOf("feedback" to "health.cardio_feedback", "strain" to "health.cardio_strain", "tolerance" to "health.cardio_tolerance", "date" to "health.cardio_date"),
        ),
        HaSensor("breathing_exercise", "Last breathing exercise", "health.breathing_exercise_at", "timestamp", null, null, "mdi:meditation", convert = Convert.LOCAL_TO_OFFSET),
        HaSensor("stairs_up", "Floors up (unconfirmed)", "health.stairs_up", null, null, "measurement", "mdi:stairs-up"),
        HaSensor("stairs_down", "Floors down (unconfirmed)", "health.stairs_down", null, null, "measurement", "mdi:stairs-down"),
        // Last workout (DW-H5600 activity log). Units that are likely, not proven, say "unconfirmed" in the name.
        HaSensor("workout_sport", "Last workout", "workout.sport", null, null, null, "mdi:run", attributes = mapOf("unconfirmed" to "workout.unconfirmed")),
        HaSensor("workout_start", "Last workout start", "workout.start", "timestamp", null, null, "mdi:clock-start"),
        HaSensor("workout_duration", "Last workout duration", "workout.duration_seconds", "duration", "min", null, "mdi:timer", convert = Convert.SECONDS_TO_MINUTES),
        HaSensor("workout_distance", "Last workout distance (unconfirmed unit, likely km)", "workout.distance", null, null, null, "mdi:map-marker-distance"),
        HaSensor("workout_kcal", "Last workout energy (unconfirmed)", "workout.kcal", null, "kcal", null, "mdi:fire"),
        HaSensor("workout_hr_avg", "Last workout average heart rate", "workout.hr_avg", null, "bpm", null, "mdi:heart-pulse"),
        HaSensor("workout_hr_max", "Last workout max heart rate", "workout.hr_max", null, "bpm", null, "mdi:heart-pulse"),
        // Decoded watch settings (read from the raw cache).
        HaSensor(
            "watch_face", "Watch face", "watch.face_index", null, null, null, "mdi:watch", "diagnostic",
            attributes = (1..4).associate { "slot_$it" to "watch.face_item.$it" },
        ),
        HaSensor("hr_measurement", "Heart rate measurement", "watch.hr_measurement", null, null, null, "mdi:heart-cog", "diagnostic"),
        HaSensor("bedtime", "Bedtime", "watch.bedtime", null, null, null, "mdi:bed-clock", "diagnostic", attributes = mapOf("wake_up" to "watch.wake_up_time")),
        HaSensor(
            "smart_alarm", "Smart alarm", "watch.smart_alarm_time", null, null, null, "mdi:alarm-check", "diagnostic",
            attributes = mapOf("on" to "watch.smart_alarm_on", "window_min" to "watch.smart_alarm_window_min"),
        ),
        // The settings Home Assistant can change (command_watch_setting): the face and the custom slot are the ones people schedule.
        HaSensor("watch_face_name", "Watch face name", "watch.setting.watch_face", null, null, null, "mdi:watch", "diagnostic"),
        HaSensor("custom_slot", "Custom face slot", "watch.setting.custom_slot", null, null, null, "mdi:watch-variant", "diagnostic"),
        HaSensor(
            "watch_writable_settings", "Watch settings (writable)", "watch.setting_count", null, null, "measurement", "mdi:tune", "diagnostic",
            attributes = keys.writableSettings.associateWith { "watch.setting.$it" },
        ),
        HaSensor("watch_settings", "Watch settings", "watch.settings.time_format", null, null, null, "mdi:cog", "diagnostic", attributes = SETTINGS_ATTRIBUTES),
    )

    /** Only the sensors a watch with [caps] has a value for ([WatchCaps.needs]): an OCW registers no health sensor. */
    fun forCaps(sensors: List<HaSensor>, caps: Set<WatchCap>): List<HaSensor> =
        sensors.filter { s -> WatchCaps.needs(s)?.let { it in caps } ?: true }

    /** The variables to resolve for an update: the states and the attributes. */
    val VARIABLES: Set<String> get() = variablesOf(ALL)

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
