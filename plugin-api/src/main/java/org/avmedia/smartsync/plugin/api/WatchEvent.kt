package org.avmedia.smartsync.plugin.api

import java.time.Instant

/**
 * What happened. The host sends the events of all watches from one place, in the order it saw them. More event types
 * may come in a compatible release, but never to a plugin built against an older [PluginApi.VERSION]: still, write
 * `else ->` in a `when`.
 */
sealed interface WatchEvent {
    /** The watch the event is about; null for an event of the app. */
    val watch: WatchId?

    /** [watch] is connected and set up. */
    data class Connected(override val watch: WatchId, val kind: ConnectionKind) : WatchEvent

    data class Disconnected(override val watch: WatchId) : WatchEvent

    /**
     * The host read values of [watch] ([PluginNeeds.values]). [values] is the whole last-known set after this read;
     * [readNow] the keys this read returned a value for (a value the watch did not give now keeps its older one). A
     * plugin that asked for no values still gets this event when another plugin asked, with the values it declared.
     */
    data class ValuesUpdated(
        override val watch: WatchId,
        val values: WatchValues,
        val cause: ValueCause,
        val readNow: Set<String> = values.values.keys,
    ) : WatchEvent {
        /** Only the values read now. */
        fun fresh(): Map<String, Any?> = values.values.filterKeys { it in readNow }
    }

    /** A health log transfer of [watch] was stored; [range] is what it covered, [kinds] what it carried. */
    data class HealthRead(override val watch: WatchId, val range: HistoryRange, val kinds: Set<DataKind<*>>) : WatchEvent

    /** A workout of [watch] was read and stored (also one read again: same [WorkoutRecord.id]). */
    data class WorkoutStored(override val watch: WatchId, val workout: WorkoutRecord) : WatchEvent

    /** The watch's button asked the app to run its actions (only to plugins with [PluginNeeds.buttonPress]). */
    data class ButtonPressed(override val watch: WatchId, val at: Instant) : WatchEvent

    /** A setting of [watch] changed (written by the app or a plugin); [key] is the [SettingInfo.key]. */
    data class SettingChanged(override val watch: WatchId, val key: String, val value: Any?) : WatchEvent

    /** The app's background link service started. Likely to go once the in-repo Home Assistant plugin moves out. */
    data object BackgroundStarted : WatchEvent { override val watch: WatchId? get() = null }

    /** The background link service ended (the app may be stopped soon). Likely to go, as [BackgroundStarted]. */
    data object BackgroundStopped : WatchEvent { override val watch: WatchId? get() = null }

    /** [watch] was removed from the app; its [PluginHost.storage] (watch) is gone already. */
    data class WatchRemoved(override val watch: WatchId) : WatchEvent

    /**
     * The host found that [from] is the same watch as [watch], re-paired under a new id: every per-watch store (the
     * host's and every plugin's) moved from [from] to [watch].
     */
    data class WatchRePaired(val from: WatchId, override val watch: WatchId) : WatchEvent

    /**
     * Events for this plugin were dropped (its queue was full): read the state again ([PluginHost.lastValues],
     * [PluginHost.history]). Sent in place of the first dropped event, never dropped itself.
     */
    data object EventsLost : WatchEvent { override val watch: WatchId? get() = null }

    /** The person answered a permission request ([PluginHost.requestPermissions]): [missing] is what is still not granted. */
    data class PermissionsChanged(val missing: Set<String>) : WatchEvent { override val watch: WatchId? get() = null }

    /** The periodic wake-up the plugin asked for ([PluginNeeds.wakeEveryMinutes]); also in a process started for it. */
    data object Wake : WatchEvent { override val watch: WatchId? get() = null }
}

/** Why values were read. */
enum class ValueCause {
    /** After a connection. */
    CONNECTION,
    HEALTH_READ,
    WORKOUT,
    /** With a watch button press. */
    BUTTON,
    /** A plugin asked ([WatchCommand.RefreshValues]) or a setting was written. */
    REQUEST,
    /** A full settings read. */
    SNAPSHOT,
}
