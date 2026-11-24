package org.avmedia.gshockGoogleSync.plugins

import android.content.Context
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.Delivered
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.HistoryRange
import org.avmedia.smartsync.plugin.api.HistoryRecord
import org.avmedia.smartsync.plugin.api.PairedWatch
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.smartsync.plugin.api.PluginStatus
import org.avmedia.smartsync.plugin.api.PluginStorage
import org.avmedia.smartsync.plugin.api.SettingInfo
import org.avmedia.smartsync.plugin.api.SettingsSnapshot
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.WatchCommand
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchNotification
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.avmedia.smartsync.plugin.api.WatchValues

/** One plugin's view of the app ([PluginHost]); everything goes through the [hub]. No call lets an exception out. */
class AppPluginHost(private val hub: PluginHub, private val plugin: WatchPlugin) : PluginHost {
    private val id = plugin.id
    private val prefix = PluginNamespaces.prefixOf(id)
    private val name: String get() = runCatching { plugin.name }.getOrDefault(id)

    override val context: Context get() = hub.context
    override val appVersion: String get() = hub.appVersion
    override val apiVersion: String get() = PluginApi.VERSION

    override fun watches(): List<PairedWatch> = quietly(emptyList()) { hub.watches() }
    override fun connected(): WatchId? = quietly(null) { hub.connectedWatch() }
    override fun current(): WatchId? = quietly(null) { hub.currentWatch() }
    override fun watchName(watch: WatchId): String? = quietly(null) { hub.watchName(watch) }

    private val access = runCatching { plugin.access }.getOrDefault(org.avmedia.smartsync.plugin.api.PluginAccess())
    private val refused = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Not served: the watch lacks a capability the plugin requires. */
    private fun unsupported(watch: WatchId): PluginResult.Failed? {
        val support = org.avmedia.smartsync.plugin.api.WatchSupport.of(access, hub.capsOf(watch))
        if (support !is org.avmedia.smartsync.plugin.api.WatchSupport.NotSupported) return null
        if (refused.add("watch $watch")) hub.log(id, "$watch is not served: it has no ${support.missing.joinToString { it.name.lowercase() }}")
        return PluginResult.Failed(FailReason.NOT_SUPPORTED, "this watch has no ${support.missing.joinToString { it.name.lowercase() }}")
    }

    /** Refused because the plugin did not declare it; one log line per kind of refusal. */
    private fun notDeclared(what: String): PluginResult.Failed {
        if (refused.add(what)) hub.log(id, "not declared, refused: $what")
        return PluginResult.Failed(FailReason.NOT_DECLARED, "not declared: $what")
    }

    override suspend fun notify(watch: WatchId, notification: WatchNotification, delivery: Delivery): PluginResult<Delivered> =
        if (org.avmedia.smartsync.plugin.api.Control.NOTIFICATIONS !in access.controls) notDeclared("notifications")
        else unsupported(watch) ?: HostCalls.safely { hub.scheduler.submit(id, watch, delivery, "notification") { hub.showNotification(watch, notification) } }

    override suspend fun command(watch: WatchId, command: WatchCommand, delivery: Delivery): PluginResult<Delivered> = HostCalls.safely {
        if (!AccessGuard.mayRun(access, command)) return@safely notDeclared((AccessGuard.controlsOf(command) - access.controls).joinToString { it.words })
        unsupported(watch)?.let { return@safely it }
        when (command) {
            // A read goes through the write queue itself (hub.readValues): never inside another queued write.
            WatchCommand.RefreshValues ->
                if (hub.connectedWatch() != watch) CommandScheduler.NOT_CONNECTED
                else { hub.readValues(ValueCause.REQUEST); CommandScheduler.DONE }
            is WatchCommand.ChangeSettings ->
                hub.scheduler.submit(id, watch, delivery, "settings ${command.values.keys.joinToString()}", timeoutMs = 60_000) {
                    hub.writeSettings(this, watch, command.values)
                }
        }
    }

    override fun valueKeys(): List<String> = quietly(emptyList()) { hub.values.keys().filter { AccessGuard.mayRead(access, it) } }

    override fun valueCatalog(): Map<String, String> =
        quietly(emptyMap()) { hub.values.catalog().filterKeys { AccessGuard.mayRead(access, it) } }

    override fun lastValues(watch: WatchId): WatchValues = quietly(WatchValues(watch)) {
        if (unsupported(watch) != null) WatchValues(watch) else AccessGuard.values(access, hub.values.kept(watch))
    }

    override fun settings(watch: WatchId): SettingsSnapshot {
        val v = lastValues(watch)
        val keys = v.values.keys.filter { it.startsWith("watch.setting.") || it.startsWith("watch.settings.") }
        return SettingsSnapshot(watch, v.values.filterKeys { it in keys }, keys.mapNotNull { v.readAtMillis[it] }.maxOrNull())
    }

    override fun settingCatalog(watch: WatchId): List<SettingInfo> = quietly(emptyList()) { hub.settingCatalog(watch) }

    override suspend fun <T : HistoryRecord> history(watch: WatchId, kind: DataKind<T>, range: HistoryRange): PluginResult<List<T>> =
        if (!AccessGuard.mayRead(access, kind)) notDeclared(org.avmedia.smartsync.plugin.api.DataUse.of(kind).words)
        else unsupported(watch) ?: HostCalls.safely { PluginResult.Ok(hub.history(watch, kind, range).map { AccessGuard.record(access, it) }) }

    override fun storage(): PluginStorage = NamespacedStorage(hub.kv, prefix)

    override fun storage(watch: WatchId): PluginStorage = WatchStorage(hub.kv, hub.watchStore, perWatchPrefix(), watch.key)

    override fun moveWatchData(from: WatchId, to: WatchId) = quietly(Unit) { hub.moveWatchData(from, to) }

    override fun declareNeeds(needs: PluginNeeds) = hub.runtime.declareNeeds(id, needs)

    override fun status(status: PluginStatus) = hub.health.reported(id, status)

    /** The per-watch values of a plugin always start with "plugin.": [WatchStorage.deleteAll] finds them. */
    private fun perWatchPrefix(): String = if (prefix.startsWith("plugin.")) prefix else "plugin.$id."

    /** The settings writer of this plugin (its log lines carry the plugin's name). */
    internal val settingsWriter by lazy { hub.newSettingsWriter(name) }

    private inline fun <T> quietly(fallback: T, block: () -> T): T = try { block() } catch (e: Throwable) {
        hub.failure(id, "app call failed", e); fallback
    }
}

/** The one guard of every host call that can fail: an exception becomes a Failed result, cancellation stays one. */
object HostCalls {
    suspend fun <T> safely(block: suspend () -> PluginResult<T>): PluginResult<T> = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } finally { } // RED CHECK: no catch
}
