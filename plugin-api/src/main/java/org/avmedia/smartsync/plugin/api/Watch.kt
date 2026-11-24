package org.avmedia.smartsync.plugin.api

/**
 * A watch, by the key every store of the app uses for it ("gshock-aabbccddeeff", from its Bluetooth address). A
 * re-paired watch gets a new key ([PluginHost.moveWatchData]).
 */
data class WatchId(val key: String) {
    override fun toString(): String = key
}

/** A paired watch. [capabilities] are those seen at its last connection; empty when it never connected. */
data class PairedWatch(
    val id: WatchId,
    /** The watch's own name, for example "CASIO GBX-H5600". */
    val name: String,
    val capabilities: Set<Capability>,
    /** Connected now. */
    val connected: Boolean,
)

/**
 * What a watch model can do, from the app's model table. A plugin offers only what a watch has: no health records for
 * a watch without [HEALTH_LOG], no notification for one without [NOTIFICATIONS].
 */
enum class Capability {
    /** Keeps the link up by itself; without it a watch connects by its button (or on a schedule) for a short time. */
    ALWAYS_CONNECTED,
    NOTIFICATIONS,
    BATTERY,
    TEMPERATURE,
    STEPS,
    /** A health log read in transfers: hourly steps, heart rate, energy, sleep, ... ([DataKind]). */
    HEALTH_LOG,
    HEART_RATE,
    SLEEP,
    BLOOD_OXYGEN,
    WORKOUTS,
    /** Health settings: watch face, heart rate measurement, bedtime, profile, smart alarm. */
    HEALTH_SETTINGS,
    FIRMWARE_INFO,
    HOME_TIME,
    WORLD_CITIES,
    TIMER,
    ALARMS,
    /** General watch settings (light, tones, time format, ...). */
    WATCH_SETTINGS,
}

/** How a connection came about. */
enum class ConnectionKind {
    /** The person: the watch's connect button, a tap in the app. */
    USER,

    /** The watch by itself (an always-connected watch reconnecting). */
    WATCH,

    /** The app's short background window for a health read. */
    BACKGROUND_READ,
}
