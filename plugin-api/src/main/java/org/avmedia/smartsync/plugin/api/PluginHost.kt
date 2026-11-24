package org.avmedia.smartsync.plugin.api

import android.content.Context

/**
 * What the app offers a plugin. One instance per plugin: storage, log lines and queues are that plugin's own.
 *
 * Reads ([watches], [lastValues], [settings], [history]) come from the host's stores and never touch the watch.
 * Writes to the watch ([notify], [command]) go through the host's single watch queue: one at a time, never during a
 * health or workout transfer, fair between plugins (round robin), each with a time limit and a [CommandResult].
 */
interface PluginHost {
    /** The application context (for the plugin's own Android work: network, WorkManager, its resources). */
    val context: Context

    /** The app's version name, for example "42.9.30". */
    val appVersion: String

    /** The interface version of the host ([PluginApi.VERSION] of the app). */
    val apiVersion: String

    /** The paired watches, with what each can do. */
    fun watches(): List<PairedWatch>

    /** The watch connected now (link up and set up), null for none. */
    fun connected(): WatchId?

    /** The connected watch, else the one connected last; null before the first connection. */
    fun current(): WatchId?

    /** The last name the app saw for [watch], also for a watch no longer paired; null when never seen. */
    fun watchName(watch: WatchId): String?

    /** Shows [notification] on [watch]. */
    suspend fun notify(watch: WatchId, notification: WatchNotification, delivery: Delivery = Delivery.Now): CommandResult

    /** Runs [command] on [watch]. */
    suspend fun command(watch: WatchId, command: WatchCommand, delivery: Delivery = Delivery.Now): CommandResult

    /** The values read last for [watch] (kept across restarts); empty before the first read. */
    fun lastValues(watch: WatchId): WatchValues

    /** The decoded settings of [watch] as read last (a part of [lastValues]). */
    fun settings(watch: WatchId): SettingsSnapshot

    /** The settings [WatchCommand.ChangeSettings] accepts for [watch], with their allowed values. */
    fun settingCatalog(watch: WatchId): List<SettingInfo>

    /** Stored records of [kind] for [watch] in [range], oldest first. */
    suspend fun <T : HistoryRecord> history(watch: WatchId, kind: DataKind<T>, range: HistoryRange): List<T>

    /** This plugin's storage, for all watches. Kept until the plugin's data is cleared. */
    fun storage(): PluginStorage

    /** This plugin's storage for [watch]: moved when the watch is re-paired, deleted with the watch. */
    fun storage(watch: WatchId): PluginStorage

    /**
     * The plugin found that [from] and [to] are the same watch (re-paired, new address): every per-watch store (the
     * host's and every plugin's) moves from [from] to [to].
     */
    fun moveWatchData(from: WatchId, to: WatchId)

    /** States or changes what the plugin needs ([PluginNeeds]). */
    fun declareNeeds(needs: PluginNeeds)

    /** One line in the App log, under the plugin's name. Never put tokens, URLs with secrets or message texts here. */
    fun log(line: String)

    /** One App log line for a failure, with the exception's class and message. */
    fun failure(line: String, error: Throwable)
}

/** A string store, namespaced by the host. */
interface PluginStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)

    /** Every key of this store (without the namespace). */
    fun keys(): Set<String>
}

/** When a write to the watch may happen. */
sealed interface Delivery {
    /** Only while the watch is connected now; else [CommandResult.NotConnected]. */
    data object Now : Delivery

    /**
     * Now when connected, else at the watch's next connection (a watch that connects by its button). Kept in memory
     * for [keepMillis] at most; a newer entry of the same [key] replaces the older one (null key: never replaced).
     */
    data class NextConnection(val keepMillis: Long = 24 * 3_600_000L, val key: String? = null) : Delivery
}

/** How a write ended. */
sealed interface CommandResult {
    data object Done : CommandResult

    /** Kept for the next connection ([Delivery.NextConnection]). */
    data object Queued : CommandResult
    data object NotConnected : CommandResult

    /** The watch (or this app) does not have it: no notifications on this model, an unknown setting, ... */
    data class NotSupported(val reason: String) : CommandResult

    /** A transfer or another write held the link for longer than the time limit. */
    data object Busy : CommandResult

    /** The values were checked and refused before anything was written. */
    data class Refused(val reason: String) : CommandResult
    data class Failed(val reason: String) : CommandResult
}
