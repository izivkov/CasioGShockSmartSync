package org.avmedia.smartsync.plugin.api

import android.content.Context

/**
 * What the app offers a plugin. One instance per plugin: storage, log lines and queues are that plugin's own.
 *
 * No call throws: each one that can fail returns a [PluginResult]. Reads ([watches], [lastValues], [settings],
 * [history]) come from the host's stores and never touch the watch. Writes to the watch ([notify], [command]) go
 * through the host's single watch queue: one at a time, never during a health or workout transfer, fair between
 * plugins (round robin), each with a time limit.
 *
 * The host does not wake a plugin later: work that must survive the app being stopped (a send after a back-off, a
 * daily job) is the plugin's own, with WorkManager through [context].
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
    suspend fun notify(watch: WatchId, notification: WatchNotification, delivery: Delivery = Delivery.Now): PluginResult<Delivered>

    /** Runs [command] on [watch]. */
    suspend fun command(watch: WatchId, command: WatchCommand, delivery: Delivery = Delivery.Now): PluginResult<Delivered>

    /** Every value key the host can read ([WatchValues]), in a stable order. */
    fun valueKeys(): List<String>

    /** The same keys with what each holds, in plain words (units, formats), in the same order. */
    fun valueCatalog(): Map<String, String>

    /** The values read last for [watch] (kept across restarts); empty before the first read. */
    fun lastValues(watch: WatchId): WatchValues

    /** The decoded settings of [watch] as read last (a part of [lastValues]). */
    fun settings(watch: WatchId): SettingsSnapshot

    /** The settings [WatchCommand.ChangeSettings] accepts for [watch], with their allowed values. */
    fun settingCatalog(watch: WatchId): List<SettingInfo>

    /** Stored records of [kind] for [watch] in [range], oldest first. A store that cannot be read is a Failed result. */
    suspend fun <T : HistoryRecord> history(watch: WatchId, kind: DataKind<T>, range: HistoryRange): PluginResult<List<T>>

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

    /**
     * How the plugin is doing, for its card in Settings: an [PluginStatus.Error] shows as "Last error: <text>, <time>"
     * until the next [PluginStatus.Ok]. A plugin reports its own failures here (a server that cannot be reached after its
     * retries) instead of throwing or retrying for ever.
     */
    fun status(status: PluginStatus)
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
    /** Only while the watch is connected now; else Failed with [FailReason.NOT_CONNECTED]. */
    data object Now : Delivery

    /**
     * Now when connected, else at the watch's next connection (a watch that connects by its button). Kept in memory
     * for [keepMillis] at most; a newer entry of the same [key] replaces the older one (null key: never replaced).
     */
    data class NextConnection(val keepMillis: Long = 24 * 3_600_000L, val key: String? = null) : Delivery
}

/** How a write was delivered. */
enum class Delivered {
    NOW,

    /** Kept for the next connection ([Delivery.NextConnection]); a later failure is logged by the host. */
    AT_NEXT_CONNECTION,
}

/** The result of a host call that can fail. Nothing crosses the interface as an exception. */
sealed interface PluginResult<out T> {
    data class Ok<T>(val value: T) : PluginResult<T>

    data class Failed(val reason: FailReason, val message: String) : PluginResult<Nothing>

    fun valueOrNull(): T? = (this as? Ok)?.value

    companion object {
        fun failed(reason: FailReason, message: String): PluginResult<Nothing> = Failed(reason, message)
    }
}

enum class FailReason {
    NOT_CONNECTED,

    /** The watch (or this app) does not have it: no notifications on this model, an unknown setting, ... */
    NOT_SUPPORTED,

    /** A transfer or another write held the link for longer than the time limit. */
    BUSY,
    TIMEOUT,

    /** Checked and refused before anything was written (a bad value). */
    INVALID,

    /** Something failed inside the host (a store that cannot be read, a write that failed). */
    INTERNAL,

    /** The plugin did not declare this data or control ([PluginAccess]). */
    NOT_DECLARED,
}

/** What a plugin tells about itself ([PluginHost.status]). */
sealed interface PluginStatus {
    val text: String

    data class Ok(override val text: String = "") : PluginStatus

    data class Error(override val text: String) : PluginStatus
}

/**
 * Logging: a plugin logs with Timber, tagged with its id (`Timber.tag("home_assistant").i(...)`), like the rest of the
 * app. In this app info and above also reach the App log file under that tag; a host without a file log has Timber only.
 * Never put a token, a webhook id, a URL with a secret or a notification's text in a line: pass such values through
 * [Redact.secret].
 */
object Redact {
    /** [value] masked for a log line: the first 2 characters and the length ("ab…(32)"); null or "" gives "". */
    fun secret(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return value // RED CHECK
        return value.take(2) + "…(" + value.length + ")"
    }

    /** [url] without its query, fragment and user info, for a log line: "https://ha.example:8123/api/webhook/…". */
    fun url(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val noQuery = url.substringBefore('?').substringBefore('#')
        val scheme = noQuery.substringBefore("://", "")
        val rest = noQuery.substringAfter("://").substringAfter('@')
        val host = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")
        return (if (scheme.isNotEmpty()) "$scheme://" else "") + host + if (path.isNotEmpty()) "/…" else ""
    }
}
