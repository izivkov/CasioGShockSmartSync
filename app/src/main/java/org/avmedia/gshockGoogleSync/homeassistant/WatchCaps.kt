package org.avmedia.gshockGoogleSync.homeassistant


import org.avmedia.gshockGoogleSync.watchvalues.HeartRateDayVariables
import org.avmedia.gshockGoogleSync.watchvalues.SleepVariables
/**
 * What a watch model has, for the Home Assistant sensors it registers: from the library's model table (WatchInfo) of
 * the connected watch. An OCW-S7000 (model OCW_S400) has no health, no workouts and no settings: it gets battery,
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

    /** The connected watch's caps from the library's model table. */
    fun connected(): Set<WatchCap> {
        val w = org.avmedia.gshockapi.WatchInfo
        val h5600 = org.avmedia.gshockGoogleSync.session.WatchModelOverride.isH5600Family(w.name) ||
            w.model == org.avmedia.gshockapi.WatchInfo.WatchModel.DW_H5600
        return buildSet {
            if (h5600) add(WatchCap.H5600)
            if (w.hasStepCounter) add(WatchCap.STEP_COUNTER)
            if (w.hasBatteryLevel) add(WatchCap.BATTERY)
            if (w.hasTemperature) add(WatchCap.TEMPERATURE)
            if (w.hasHomeTime) add(WatchCap.HOME_TIME)
            if (w.hasTimer) add(WatchCap.TIMER)
            if (w.hasWatchSettings) add(WatchCap.WATCH_SETTINGS)
        }
    }

    /** The capability a sensor's value needs; null = every watch has it (connection, sync, phone, push). */
    fun needs(s: HaSensor): WatchCap? {
        val v = s.variable
        return when {
            v == "health.steps_today" -> WatchCap.STEP_COUNTER
            v.startsWith("health.") || v.startsWith("workout.") -> WatchCap.H5600
            v in SleepVariables.DESCRIPTIONS || v in HeartRateDayVariables.DESCRIPTIONS -> WatchCap.H5600
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
