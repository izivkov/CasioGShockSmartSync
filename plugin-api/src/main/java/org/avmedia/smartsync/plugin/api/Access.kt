package org.avmedia.smartsync.plugin.api

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a plugin uses, declared once ([WatchPlugin.access]). The person sees it before turning the plugin on, and the
 * host keeps to it: data of an undeclared kind is not served or delivered, an undeclared control is refused
 * ([FailReason.NOT_DECLARED]). A checked promise to the person, not a sandbox: plugins are compiled into the app.
 */
data class PluginAccess(
    /** The watch and phone data the plugin reads. */
    val reads: Set<DataUse> = emptySet(),
    /** What it may make the watch do. */
    val controls: Set<Control> = emptySet(),
    /** Where its data goes, in a few plain words ("your Home Assistant server", "Health Connect on this phone"). */
    val sendsTo: List<String> = emptyList(),
    /** What it keeps itself ("Home Assistant URL and token"). */
    val stores: List<String> = emptyList(),
    /**
     * What a watch must have for the plugin to work at all (Health Connect: [Capability.HEALTH_LOG]). Never model
     * names: a new model with the same capabilities works without a plugin update.
     */
    val requires: Set<Capability> = emptySet(),
    /** What the plugin uses where a watch has it (Home Assistant: notifications, settings, health, ...). */
    val uses: Set<Capability> = emptySet(),
    /**
     * Android permissions the plugin itself needs, each with a one-line reason (Health Connect's write permissions,
     * POST_NOTIFICATIONS for its own notifications). Phone data the host has ([DataUse.PHONE_LOCATION],
     * [DataUse.PHONE_BATTERY]) is declared under [reads] instead: the host asks for it. The host shows these, asks for
     * the runtime ones when the plugin is turned on ([PluginHost.requestPermissions]) and shows "Needs permission" while
     * one is missing. List only what the plugin uses: its manifest permissions become the app's.
     */
    val androidPermissions: List<AndroidPermission> = emptyList(),
)

/** An Android permission a plugin needs ([PluginAccess.androidPermissions]): its name and why, in a few plain words. */
data class AndroidPermission(val name: String, val reason: String)

/**
 * A kind of data a plugin may read. The host states the kind of every value ([ValueInfo.use]); a value without one is
 * not served to any plugin.
 */
enum class DataUse {
    /** Steps, distance, energy, activity time. */
    STEPS,
    HEART_RATE,
    /** Sleep, nightly recharge, breathing. */
    SLEEP,
    BLOOD_OXYGEN,
    WORKOUTS,
    BATTERY_AND_TEMPERATURE,
    /** Watch settings, alarms, cities, timer. */
    WATCH_SETTINGS,
    /** The body profile kept on the watch: height, weight, sex, heart rate limits, VO2max. */
    BODY_PROFILE,
    /** Watch name, model, firmware, connection. */
    WATCH_INFO,
    /** Phone time zone and app state. */
    PHONE,
    /** The phone's battery level. */
    PHONE_BATTERY,
    /**
     * The phone's coarse location (last known, about 1 km), read by the host: the host asks the person for the location
     * permission, a plugin never declares one for this.
     */
    PHONE_LOCATION;

    companion object {
        /** What a stored record of [kind] needs. */
        fun of(kind: DataKind<*>): DataUse = when (kind) {
            DataKind.Days, DataKind.Hours -> STEPS
            DataKind.HeartRate -> HEART_RATE
            DataKind.Nights -> SLEEP
            DataKind.Workouts -> WORKOUTS
        }
    }
}

/** What a plugin may make the watch or the app do. */
enum class Control {
    NOTIFICATIONS,
    WATCH_SETTINGS,
    /** The watch face settings ([SettingInfo.control] says which rows). */
    WATCH_FACE,
    /** Run on a press of the watch button ([PluginNeeds.buttonPress]). */
    BUTTON_PRESS,
}

/** A plugin's icon: a drawable of its own resources or a vector. The host has a fallback for none. */
sealed interface PluginIcon {
    data class Drawable(@DrawableRes val id: Int) : PluginIcon

    data class Vector(val image: ImageVector) : PluginIcon
}
