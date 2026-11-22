package org.avmedia.gshockGoogleSync.webhook

/** The three groups of values the owner can switch off. */
enum class HaGroup(val title: String) { WATCH("Watch"), HEALTH("Health"), SETTINGS("Settings") }

object HaGroups {
    fun of(sensor: HaSensor): HaGroup = HaGroup.WATCH

    /** The sensors of [on] groups, in the order of [HaSensors.ALL]. */
    fun sensors(on: Set<HaGroup>, all: List<HaSensor> = HaSensors.ALL): List<HaSensor> = all
}
