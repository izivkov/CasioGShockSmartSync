package org.avmedia.gshockGoogleSync.homeassistant

/** The three groups of values the owner can switch off. */
enum class HaGroup(val title: String) { WATCH("Watch"), HEALTH("Health"), SETTINGS("Settings") }

object HaGroups {
    fun of(sensor: HaSensor): HaGroup = when {
        sensor.uniqueId == "watch_settings" || sensor.uniqueId == "watch_writable_settings" -> HaGroup.SETTINGS
        sensor.variable.startsWith("health.") || sensor.variable.startsWith("workout.") -> HaGroup.HEALTH
        else -> HaGroup.WATCH
    }

    /** The sensors of [on] groups, in the order of [HaSensors.ALL]. */
    fun sensors(on: Set<HaGroup>, all: List<HaSensor> = HaSensors.ALL): List<HaSensor> = all.filter { of(it) in on }
}
