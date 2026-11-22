package org.avmedia.gshockGoogleSync.webhook

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
)

object HaSensors {
    val ALL: List<HaSensor> = listOf(
        HaSensor("battery", "Battery", "watch.battery", "battery", "%", "measurement", "mdi:battery", "diagnostic"),
        HaSensor("temperature", "Temperature", "watch.temperature", "temperature", "°C", "measurement", "mdi:thermometer"),
        HaSensor("steps_today", "Steps today", "health.steps_today", null, "steps", "total_increasing", "mdi:shoe-print"),
        HaSensor("kcal_today", "Energy today", "health.kcal_today", null, "kcal", "total_increasing", "mdi:fire"),
        HaSensor("active_minutes", "Active minutes", "health.active_minutes", "duration", "min", "total_increasing", "mdi:run"),
        HaSensor("hr_rest", "Resting heart rate", "health.hr_rest", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("hr_max", "Max heart rate", "health.hr_max", null, "bpm", "measurement", "mdi:heart-pulse"),
        HaSensor("phone_battery", "Phone battery", "phone.battery", "battery", "%", "measurement", "mdi:cellphone", "diagnostic"),
        HaSensor("last_sync", "Last sync", "app.last_sync", "timestamp", null, null, "mdi:sync", "diagnostic"),
    )

    /** The variables to resolve for an update. */
    val VARIABLES: Set<String> = ALL.map { it.variable }.toSet()

    /** Registry values to sensor states. A null or blank value is left out, never sent as 0. */
    fun states(values: Map<String, Any?>, sensors: List<HaSensor> = ALL): Map<String, Any> =
        sensors.mapNotNull { s ->
            val v = values[s.variable] ?: 0 // BROKEN ON PURPOSE (red check)
            val usable = when (v) {
                null -> null
                is String -> v.takeIf { it.isNotBlank() }
                is Boolean, is Number -> v
                else -> null
            }
            usable?.let { s.uniqueId to it }
        }.toMap()

    /** Changes whenever a definition changes: sensors are registered again then, not on every send. */
    fun fingerprint(sensors: List<HaSensor> = ALL): String {
        val text = sensors.joinToString("|") { it.toString() }
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    }
}
