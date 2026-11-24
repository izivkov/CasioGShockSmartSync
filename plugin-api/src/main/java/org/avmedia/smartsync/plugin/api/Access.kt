package org.avmedia.smartsync.plugin.api

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a plugin uses, declared once ([WatchPlugin.access]). The person sees it before turning the plugin on, and the
 * host keeps to it: data of an undeclared kind is not served or delivered, an undeclared control is refused
 * ([FailReason.NOT_DECLARED]).
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
)

/** How far a plugin works with one watch ([PluginAccess.requires], [PluginAccess.uses]). */
sealed interface WatchSupport {
    /** Everything the plugin uses. */
    data object Full : WatchSupport

    /** It works, without [missing]. */
    data class Partial(val missing: Set<Capability>) : WatchSupport

    /** The watch lacks [missing], which the plugin requires: it gets nothing for this watch. */
    data class NotSupported(val missing: Set<Capability>) : WatchSupport

    companion object {
        /** For a watch with [caps]; an empty set (a watch never connected) counts as not known yet: [Full]. */
        fun of(access: PluginAccess, caps: Set<Capability>): WatchSupport {
            if (caps.isEmpty()) return Full
            val needed = access.requires - caps
            if (needed.isNotEmpty()) return NotSupported(needed)
            val missing = access.uses - caps
            return if (missing.isEmpty()) Full else Partial(missing)
        }
    }
}

/** A kind of data a plugin may read. [words] is how the app names it. */
enum class DataUse(val words: String) {
    STEPS("steps, distance and energy"),
    HEART_RATE("heart rate"),
    SLEEP("sleep"),
    BLOOD_OXYGEN("blood oxygen"),
    WORKOUTS("workouts"),
    BATTERY_AND_TEMPERATURE("watch battery and temperature"),
    WATCH_SETTINGS("watch settings, alarms and cities"),
    WATCH_INFO("watch name, model and firmware"),
    PHONE("phone battery, time zone and app state");

    companion object {
        /** The kind of data the value [key] ([WatchValues]) is. */
        fun ofValueKey(key: String): DataUse = when {
            key.startsWith("workout.") -> WORKOUTS
            key.startsWith("health.spo2") -> BLOOD_OXYGEN
            key.startsWith("health.sleep") || key.startsWith("health.recharge") || key.startsWith("health.breathing") -> SLEEP
            key.startsWith("health.hr") || key.startsWith("health.cardio") -> HEART_RATE
            key.startsWith("health.") -> STEPS
            key == "watch.battery" || key.startsWith("watch.battery_") || key == "watch.temperature" -> BATTERY_AND_TEMPERATURE
            key == "watch.name" || key == "watch.model" || key.startsWith("watch.firmware") || key == "watch.connected" ||
                key == "watch.notifications_supported" -> WATCH_INFO
            key.startsWith("watch.") || key.startsWith("alarms.") -> WATCH_SETTINGS
            else -> PHONE
        }

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
enum class Control(val words: String) {
    NOTIFICATIONS("show notifications on the watch"),
    WATCH_SETTINGS("change watch settings"),
    WATCH_FACE("change the watch face"),
    BUTTON_PRESS("run on a press of the watch button");

    companion object {
        /** The settings that are the watch face ([WatchCommand.ChangeSettings] keys). */
        val FACE_SETTINGS = setOf("watch_face", "custom_slot")

        fun ofSetting(key: String): Control = if (key in FACE_SETTINGS) WATCH_FACE else WATCH_SETTINGS
    }
}

/** A plugin's icon: a drawable of its own resources or a vector. The host has a fallback for none. */
sealed interface PluginIcon {
    data class Drawable(@DrawableRes val id: Int) : PluginIcon

    data class Vector(val image: ImageVector) : PluginIcon
}
