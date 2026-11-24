package org.avmedia.smartsync.plugin.api

import android.content.Context

/**
 * What the app offers a plugin. One instance per plugin and activation: storage, log lines and queues are that
 * plugin's own.
 *
 * No call throws: each one that can fail returns a [PluginResult]. Reads ([watches], [lastValues], [settings],
 * [history]) come from the host's stores and never touch the watch. Writes to the watch ([notify], [command]) go
 * through the host's single watch queue: one at a time, never during a health or workout transfer, fair between
 * plugins (round robin), each with a time limit. Time a plugin spends waiting in these calls does not count against
 * its own callback limits ([WatchPlugin]).
 *
 * After [WatchPlugin.deactivate] this instance is revoked: suspend calls give `Failed(STOPPED)`, reads give empty
 * results, [declareNeeds] and [status] do nothing; [storage] still works (a plugin's screen may run while it is off).
 *
 * Work that must happen later or survive the app being stopped: ask for [PluginNeeds.wakeEveryMinutes] and handle
 * [WatchEvent.Wake]. A WorkManager job of the plugin's own runs in a process where the plugin may not be started.
 */
interface PluginHost {
    /**
     * The application context (for the plugin's own Android work: network, its resources). Plugins are compiled into
     * the app: the context reaches more than this interface does, and the access declaration is a promise the host
     * checks for what goes through this interface only.
     */
    val context: Context

    /** The app's version name, for example "42.9.30". */
    val appVersion: String

    /** The interface version of the host ([PluginApi.VERSION] of the app). */
    val apiVersion: String

    /** The paired watches, with what each can do ([PairedWatch.connected] says which is connected now). */
    fun watches(): List<PairedWatch>

    /** The watch connected now (link up and set up), null for none. */
    fun connected(): WatchId?

    /** The connected watch, else the one connected last; null before the first connection. Likely to go: use [watches]. */
    fun current(): WatchId?

    /** The last name the app saw for [watch], also for a watch no longer paired; null when never seen. */
    fun watchName(watch: WatchId): String?

    /** Shows [notification] on [watch] (needs [Control.NOTIFICATIONS]). */
    suspend fun notify(watch: WatchId, notification: WatchNotification, delivery: Delivery = Delivery.Now): PluginResult<Delivered>

    /** Runs [command] on [watch]. */
    suspend fun command(watch: WatchId, command: WatchCommand, delivery: Delivery = Delivery.Now): PluginResult<Delivered>

    /** Every value key the host can read ([WatchValues]) that this plugin declared, with its kind, type and unit, in a stable order. */
    fun valueCatalog(): List<ValueInfo>

    /** The values read last for [watch] (kept across restarts); empty before the first read. */
    fun lastValues(watch: WatchId): WatchValues

    /** The decoded settings of [watch] as read last, by [SettingInfo.key]; read at each connection with [PluginNeeds.settings]. */
    fun settings(watch: WatchId): SettingsSnapshot

    /** The settings [WatchCommand.ChangeSettings] accepts for [watch] (only those its capabilities have), with their allowed values. */
    fun settingCatalog(watch: WatchId): List<SettingInfo>

    /**
     * Stored records of [kind] for [watch] in [range], oldest first. A store that cannot be read, a watch the host never
     * saw or a range over 400 days is a Failed result.
     */
    suspend fun <T : HistoryRecord> history(watch: WatchId, kind: DataKind<T>, range: HistoryRange): PluginResult<List<T>>

    /**
     * This plugin's storage, for all watches. Kept until the person deletes the plugin's data (Settings > Plugins). Not
     * encrypted, and backed up with the app's data.
     */
    fun storage(): PluginStorage

    /** This plugin's storage for [watch]: moved when the host finds the watch re-paired, deleted with the watch. */
    fun storage(watch: WatchId): PluginStorage

    /**
     * The Android permissions of [PluginAccess.androidPermissions] (and the location permission for
     * [DataUse.PHONE_LOCATION]) that were missing at the host's last check; empty when all are. Never blocks: the host
     * checks at start, after a turn-on or an answer and when the app comes to the front. A permission revoked in the
     * system settings shows here only, without a [WatchEvent.PermissionsChanged].
     */
    fun missingPermissions(): Set<String>

    /**
     * Asks the person for [missingPermissions] through the app's screen (the host also does when the plugin is turned
     * on). Call it from the plugin's screen; it returns at once, the answer comes as [WatchEvent.PermissionsChanged].
     * With [WatchPlugin.permissionIntent] the host opens that screen instead.
     */
    fun requestPermissions()

    /** States or changes what the plugin needs ([PluginNeeds]). */
    fun declareNeeds(needs: PluginNeeds)

    /**
     * How the plugin is doing, for its line in Settings > Plugins: [PluginStatus.Ok] shows its text ("Connected", "Not
     * set up"); an [PluginStatus.Error] shows as "Last error: <text>, <time>" until the next [PluginStatus.Ok]. A plugin
     * reports its own failures here (a server that cannot be reached after its retries) instead of throwing or retrying
     * for ever.
     */
    fun status(status: PluginStatus)
}

/**
 * A string store, namespaced by the host. Keys are `[A-Za-z0-9._-]` (no `@`: the per-watch store uses it). Every call
 * is durable on return and may block on a disk write: never call it from the main thread. No call throws; a refused
 * key or a store over its bound (2000 keys, 64 KiB a value) is a log line and nothing written.
 */
interface PluginStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)

    /** Writes [values] in one disk write: a null value deletes its key. */
    fun putAll(values: Map<String, String?>)

    /** Every key of this store (without the namespace). */
    fun keys(): Set<String>
}

/** When a write to the watch may happen. */
sealed interface Delivery {
    /** Only while the watch is connected now; else Failed with [FailReason.NOT_CONNECTED]. */
    data object Now : Delivery

    /**
     * Now when connected, else at the watch's next connection (a watch that connects by its button). Kept in memory
     * only, for [keepMillis] at most (up to 7 days); a newer entry of the same [key] replaces the older one (null key:
     * never replaced). Lost when the app process ends, when the plugin stops and when the watch is removed:
     * `Ok(AT_NEXT_CONNECTION)` means accepted, not delivered, so a plugin that must deliver resends from its own state
     * on [WatchEvent.Connected].
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
}

/** Why a call failed. More reasons may come in a compatible release: write `else ->` in a `when`. */
enum class FailReason {
    NOT_CONNECTED,

    /** The watch (or this app) does not have it: no notifications on this model, an unknown setting, ... */
    NOT_SUPPORTED,

    /** A transfer or other writes held the link for longer than the time limit, or the plugin has too many writes queued. */
    BUSY,

    /** No answer in time: the write may or may not have reached the watch. */
    TIMEOUT,

    /** Checked and refused before anything was written (a bad value, an unknown watch, a range too long). */
    INVALID,

    /** Something failed inside the host (a store that cannot be read, a write that failed). */
    INTERNAL,

    /** The plugin did not declare this data or control ([PluginAccess]). */
    NOT_DECLARED,

    /** The plugin was stopped: this host is revoked ([WatchPlugin.deactivate]). */
    STOPPED,
}

/** What a plugin tells about itself ([PluginHost.status]). */
sealed interface PluginStatus {
    val text: String

    data class Ok(override val text: String = "") : PluginStatus

    data class Error(override val text: String) : PluginStatus
}

/**
 * Logging: a plugin logs with Timber (5.x), tagged with its id (`Timber.tag("home_assistant").i(...)`), like the rest of
 * the app. In this app info and above also reach the App log file under that tag; a host without a file log has Timber
 * only. Never put a token, a webhook id, a URL with a secret or a notification's text in a line: pass such values
 * through [Redact.secret] and [Redact.url].
 */
object Redact {
    /** [value] masked for a log line: the first 2 characters and the length ("ab…(32)"); null or "" gives "". */
    fun secret(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        if (value.length <= 4) return "…(${value.length})"
        return value.take(2) + "…(" + value.length + ")"
    }

    /** [url] without its query, fragment, user info and path, for a log line: "https://ha.example:8123/…". */
    fun url(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val noQuery = url.substringBefore('?').substringBefore('#')
        val scheme = noQuery.substringBefore("://", "")
        val rest = if (scheme.isNotEmpty()) noQuery.substringAfter("://") else noQuery
        // The authority ends at the first '/': a user part ends at its last '@' (a password may hold one).
        val authority = rest.substringBefore('/')
        val host = authority.substringAfterLast('@')
        val path = rest.substringAfter('/', "")
        return (if (scheme.isNotEmpty()) "$scheme://" else "") + host + if (path.isNotEmpty()) "/…" else ""
    }
}
