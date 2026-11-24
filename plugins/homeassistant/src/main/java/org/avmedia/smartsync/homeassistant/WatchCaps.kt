package org.avmedia.smartsync.homeassistant

import org.avmedia.smartsync.plugin.api.Capability
/**
 * What a watch model has, for the Home Assistant sensors it registers: from the host's capabilities of the watch. An OCW-S7000 (model OCW_S400) has no health, no workouts and no settings: it gets battery,
 * temperature, connection, time sync and the phone values only.
 */
enum class WatchCap {
    /** The DW-H5600 family: LifeLog health, sleep, heart rate, workouts, face and health settings, firmware. */
    H5600,
    STEP_COUNTER,
    BATTERY,
    TEMPERATURE,
    HOME_TIME,
    TIMER,
    WATCH_SETTINGS,
}

object WatchCaps {
    /** Every capability: what an older job without caps meant for an H5600 watch. */
    val ALL: Set<WatchCap> = WatchCap.entries.toSet()

    fun names(caps: Set<WatchCap>): List<String> = caps.map { it.name }.sorted()

    /** The caps of a job; an older job without them: all for an H5600 watch, all but [WatchCap.H5600] for another. */
    fun of(job: HaJob): Set<WatchCap> =
        job.caps?.mapNotNull { n -> WatchCap.entries.firstOrNull { it.name == n } }?.toSet()
            ?: if (job.h5600 == false) ALL - WatchCap.H5600 else ALL

    /** A watch's caps from what the host says it can do ([Capability], from the app's model table). */
    fun of(caps: Set<Capability>): Set<WatchCap> = buildSet {
        if (Capability.HEALTH_LOG in caps) add(WatchCap.H5600)
        if (Capability.STEPS in caps) add(WatchCap.STEP_COUNTER)
        if (Capability.BATTERY in caps) add(WatchCap.BATTERY)
        if (Capability.TEMPERATURE in caps) add(WatchCap.TEMPERATURE)
        if (Capability.HOME_TIME in caps) add(WatchCap.HOME_TIME)
        if (Capability.TIMER in caps) add(WatchCap.TIMER)
        if (Capability.WATCH_SETTINGS in caps) add(WatchCap.WATCH_SETTINGS)
    }

    /** The capability a sensor's value needs; null = every watch has it (connection, sync, phone, push). */
    fun needs(s: HaSensor): WatchCap? {
        val v = s.variable
        return when {
            v == "health.steps_today" -> WatchCap.STEP_COUNTER
            v.startsWith("health.") || v.startsWith("workout.") -> WatchCap.H5600
            v == "watch.battery" -> WatchCap.BATTERY
            v == "watch.temperature" -> WatchCap.TEMPERATURE
            v == "watch.home_city" || v == "watch.dst.home" -> WatchCap.HOME_TIME
            v == "watch.timer_seconds" -> WatchCap.TIMER
            v.startsWith("watch.settings.") -> WatchCap.WATCH_SETTINGS
            v.startsWith("watch.setting") || v.startsWith("watch.face") || v.startsWith("watch.raw") -> WatchCap.H5600
            v.startsWith("watch.firmware") || v in H5600_SETTINGS -> WatchCap.H5600
            else -> null
        }
    }

    private val H5600_SETTINGS = setOf("watch.hr_measurement", "watch.bedtime", "watch.smart_alarm_time")
}
