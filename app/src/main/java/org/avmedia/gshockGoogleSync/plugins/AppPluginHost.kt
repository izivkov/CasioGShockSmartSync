package org.avmedia.gshockGoogleSync.plugins

import android.content.Context
import org.avmedia.gshockGoogleSync.watchvalues.SettingsCheck
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingRegistry
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingVariables
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.Delivered
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.HistoryRange
import org.avmedia.smartsync.plugin.api.HistoryRecord
import org.avmedia.smartsync.plugin.api.PairedWatch
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.smartsync.plugin.api.PluginStatus
import org.avmedia.smartsync.plugin.api.PluginStorage
import org.avmedia.smartsync.plugin.api.SettingInfo
import org.avmedia.smartsync.plugin.api.SettingsSnapshot
import org.avmedia.smartsync.plugin.api.ValueInfo
import org.avmedia.smartsync.plugin.api.WatchCommand
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchNotification
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.avmedia.smartsync.plugin.api.WatchValues

/**
 * One plugin's view of the app ([PluginHost]), one per activation; everything goes through the [hub]. No call lets an
 * exception out. Revoked when the plugin stops ([revoke]): suspend calls give STOPPED, reads give nothing, storage
 * still works.
 */
class AppPluginHost(private val hub: PluginHub, private val plugin: WatchPlugin, private val id: String = plugin.id) : PluginHost, RevocableHost {
    private val prefix = PluginNamespaces.prefixOf(id)
    private val name: String get() = runCatching { plugin.name }.getOrDefault(id)
    @Volatile private var revoked = false

    override fun revoke() {
        revoked = true
    }

    private val stopped = PluginResult.Failed(FailReason.STOPPED, "the plugin was stopped")

    override val context: Context get() = hub.context
    override val appVersion: String get() = hub.appVersion
    override val apiVersion: String get() = PluginApi.VERSION

    override fun watches(): List<PairedWatch> = read(emptyList()) { hub.watches() }
    override fun connected(): WatchId? = read(null) { hub.connectedWatch() }
    override fun current(): WatchId? = read(null) { hub.currentWatch() }
    override fun watchName(watch: WatchId): String? = read(null) { hub.watchName(watch) }

    private val access = runCatching { plugin.access }.getOrDefault(PluginAccess())
    private val refused = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val uses: ValueUses = { key -> hub.values.useOf(key) }

    /** Not served: the watch lacks a capability the plugin requires. */
    private fun unsupported(watch: WatchId): PluginResult.Failed? {
        val support = WatchSupport.of(access, hub.capsOf(watch))
        if (support !is WatchSupport.NotSupported) return null
        if (refused.add("watch $watch")) hub.log(id, "$watch is not served: it has no ${support.missing.joinToString { it.name.lowercase() }}")
        return PluginResult.Failed(FailReason.NOT_SUPPORTED, "this watch has no ${support.missing.joinToString { it.name.lowercase() }}")
    }

    /** Refused because the plugin did not declare it; one log line per kind of refusal. */
    private fun notDeclared(what: String): PluginResult.Failed {
        if (refused.add(what)) hub.log(id, "not declared, refused: $what")
        return PluginResult.Failed(FailReason.NOT_DECLARED, "not declared: $what")
    }

    override suspend fun notify(watch: WatchId, notification: WatchNotification, delivery: Delivery): PluginResult<Delivered> = call {
        if (Control.NOTIFICATIONS !in access.controls) notDeclared("notifications")
        else unsupported(watch) ?: hub.scheduler.submit(id, watch, delivery, "notification") { hub.showNotification(watch, notification) }
    }

    override suspend fun command(watch: WatchId, command: WatchCommand, delivery: Delivery): PluginResult<Delivered> = call {
        if (!AccessGuard.mayRun(access, command)) return@call notDeclared((AccessGuard.controlsOf(command) - access.controls).joinToString { AccessGuard.words(it) })
        unsupported(watch)?.let { return@call it }
        when (command) {
            WatchCommand.RefreshValues -> {
                if (access.reads.isEmpty()) return@call notDeclared("values (no data declared)")
                if (hub.connectedWatch() == watch) hub.refreshValues()
                else if (delivery is Delivery.NextConnection) CommandScheduler.QUEUED // every connection reads the values
                else CommandScheduler.NOT_CONNECTED
            }
            is WatchCommand.ChangeSettings -> when (val c = WatchSettingRegistry.checkAll(command.values, hub.watchName(watch))) {
                // All or nothing: one unknown key or bad value and nothing is written.
                is SettingsCheck.Refused -> PluginResult.Failed(c.reason, c.message)
                is SettingsCheck.Ok -> hub.scheduler.submit(id, watch, delivery, "settings ${c.values.keys.joinToString()}", timeoutMs = 60_000) {
                    hub.writeSettings(name, watch, c.values)
                }
            }
        }
    }

    override fun valueCatalog(): List<ValueInfo> = read(emptyList()) { hub.values.catalog().filter { it.use in access.reads } }

    override fun lastValues(watch: WatchId): WatchValues = read(WatchValues(watch)) {
        if (unsupported(watch) != null) WatchValues(watch) else AccessGuard.values(access, uses, hub.values.kept(watch))
    }

    override fun settings(watch: WatchId): SettingsSnapshot = read(SettingsSnapshot(watch, emptyMap(), null)) { snapshot(lastValues(watch)) }

    override fun settingCatalog(watch: WatchId): List<SettingInfo> = read(emptyList()) { hub.settingCatalog(watch) }

    override suspend fun <T : HistoryRecord> history(watch: WatchId, kind: DataKind<T>, range: HistoryRange): PluginResult<List<T>> = call {
        when {
            !AccessGuard.mayRead(access, kind) -> notDeclared(AccessGuard.words(DataUse.of(kind)))
            java.time.Duration.between(range.from, range.to).toDays() > MAX_HISTORY_DAYS -> PluginResult.Failed(FailReason.INVALID, "a range over $MAX_HISTORY_DAYS days")
            !hub.knowsWatch(watch) -> PluginResult.Failed(FailReason.INVALID, "unknown watch")
            else -> unsupported(watch) ?: PluginResult.Ok(hub.history(watch, kind, range).map { AccessGuard.record(access, it) })
        }
    }

    override fun storage(): PluginStorage = NamespacedStorage(hub.kv, prefix, checked = !PluginNamespaces.isKept(id), log = { hub.log(id, it) })

    override fun storage(watch: WatchId): PluginStorage =
        WatchStorage(hub.kv, hub.watchStore, PluginNamespaces.perWatchPrefixOf(id), watch.key, log = { hub.log(id, it) })

    override fun missingPermissions(): Set<String> = read(emptySet()) { hub.missingPermissions(id).map { it.name }.toSet() }

    override fun requestPermissions() { if (!revoked) runCatching { hub.requestPermissions(id) } }

    override fun declareNeeds(needs: PluginNeeds) { if (!revoked) hub.declareNeeds(id, needs) }

    override fun status(status: PluginStatus) { if (!revoked) hub.health.reported(id, status) }

    /** A read: nothing once revoked, the fallback when the app's call throws. */
    private inline fun <T> read(fallback: T, block: () -> T): T = if (revoked) fallback else try { block() } catch (e: Throwable) {
        hub.failure(id, "app call failed", e); fallback
    }

    /** A suspend call: STOPPED once revoked; its time does not count against the plugin's callback limit. */
    private suspend fun <T> call(block: suspend () -> PluginResult<T>): PluginResult<T> {
        if (revoked) return stopped
        hub.runtime.enterHost(id)
        return try { HostCalls.safely(block) } finally { hub.runtime.exitHost(id) }
    }

    companion object {
        const val MAX_HISTORY_DAYS = 400L

        /** The settings part of [values], by catalogue key ([SettingInfo.key]): "watch.setting.step_goal" is "step_goal". */
        fun snapshot(values: WatchValues): SettingsSnapshot {
            val keys = values.values.keys.mapNotNull { k -> WatchSettingVariables.settingKeyOf(k)?.takeIf { WatchSettingRegistry.find(it) != null }?.let { k to it } }
            return SettingsSnapshot(
                values.watch,
                keys.associate { (valueKey, key) -> key to values.values[valueKey] },
                keys.mapNotNull { values.readAtMillis[it.first] }.maxOrNull(),
            )
        }
    }
}

/** The one guard of every host call that can fail: an exception becomes a Failed result, cancellation stays one. */
object HostCalls {
    suspend fun <T> safely(block: suspend () -> PluginResult<T>): PluginResult<T> = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        PluginResult.Failed(FailReason.INTERNAL, e.javaClass.simpleName)
    }
}
