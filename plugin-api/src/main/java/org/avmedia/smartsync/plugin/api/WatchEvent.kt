package org.avmedia.smartsync.plugin.api

import java.time.Instant

/** What happened. Events of one watch come in the order they happened. */
sealed interface WatchEvent {
    /** The watch the event is about; null for an event of the app. */
    val watch: WatchId?

    /** [watch] is connected and set up. */
    data class Connected(override val watch: WatchId, val kind: ConnectionKind) : WatchEvent

    data class Disconnected(override val watch: WatchId) : WatchEvent

    /** The host read values of [watch] ([PluginNeeds.values]); [values] is the whole last-known set after this read. */
    data class ValuesUpdated(override val watch: WatchId, val values: WatchValues, val cause: ValueCause) : WatchEvent

    /** A health log transfer of [watch] was stored; [range] is what it covered, [kinds] what it carried. */
    data class HealthRead(override val watch: WatchId, val range: HistoryRange, val kinds: Set<DataKind<*>>) : WatchEvent

    /** A workout of [watch] was read and stored (also one read again: same [WorkoutRecord.id]). */
    data class WorkoutStored(override val watch: WatchId, val workout: WorkoutRecord) : WatchEvent

    /** The watch's button asked the app to run its actions (only to plugins with [PluginNeeds.buttonPress]). */
    data class ButtonPressed(override val watch: WatchId, val at: Instant) : WatchEvent

    /** A setting of [watch] changed (written by the app or a plugin, or read back different). */
    data class SettingChanged(override val watch: WatchId, val key: String, val value: Any?) : WatchEvent

    /** Battery and temperature as read now; null = not read. */
    data class Condition(override val watch: WatchId, val batteryPercent: Int?, val temperatureC: Int?) : WatchEvent

    /** The app's background link service started: plugins that keep a connection of their own may start it now. */
    data object BackgroundStarted : WatchEvent { override val watch: WatchId? get() = null }

    /** The background link service ended (the app may be stopped soon). */
    data object BackgroundStopped : WatchEvent { override val watch: WatchId? get() = null }

    /** [watch] was removed from the app; its [PluginHost.storage] (watch) is gone already. */
    data class WatchRemoved(override val watch: WatchId) : WatchEvent
}

/** Why values were read. [wire] is a stable word for payloads ("sync", "health_data", ...). */
enum class ValueCause(val wire: String) {
    /** After a connection. */
    CONNECTION("sync"),
    HEALTH_READ("health_data"),
    WORKOUT("workout_downloaded"),
    /** With a watch button press. */
    BUTTON("watch_action"),
    /** A plugin asked ([WatchCommand.RefreshValues]) or a setting was written. */
    REQUEST("sync"),
    /** A full settings read. */
    SNAPSHOT("snapshot"),
}
