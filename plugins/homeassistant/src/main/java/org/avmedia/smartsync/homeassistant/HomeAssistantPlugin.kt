package org.avmedia.smartsync.homeassistant

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.avmedia.smartsync.plugin.api.Capability
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginIcon
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.smartsync.plugin.api.PluginStatus
import org.avmedia.smartsync.plugin.api.PluginUi
import org.avmedia.smartsync.plugin.api.WatchCommand
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchNotification
import org.avmedia.smartsync.plugin.api.WatchPlugin

/**
 * Home Assistant as a plugin: one HA device per watch (mobile_app), its sensors, the watch button event and HA's
 * notifications and commands to the watch over the push socket. Found by the app through ServiceLoader.
 */
class HomeAssistantPlugin : WatchPlugin {
    override val id: String = ID
    override val name: String = "Home Assistant"
    override val summary: String = "Your watch as a Home Assistant device."
    override val description: String = "Shows your watch in Home Assistant: battery, steps, heart rate, sleep and workouts as sensors. " +
        "Lets automations send notifications to the watch and change its settings, and use the watch button as a trigger."
    override val setupNeeds: String = "Your Home Assistant address and an access token"
    override val infoUrl: String = "https://github.com/ExcuseMi/CasioGShockSmartSync/blob/home-assistant/docs/home-assistant.md"
    override val sourceUrl: String = "https://github.com/ExcuseMi/CasioGShockSmartSync/tree/home-assistant/plugins/homeassistant"
    override val issuesUrl: String = "https://github.com/ExcuseMi/CasioGShockSmartSync/issues"
    override val author: String = "ExcuseMi"
    override val license: String = "Apache-2.0"
    override val apiVersion: String = PluginApi.VERSION
    override val version: String = VERSION

    override val icon: PluginIcon = PluginIcon.Drawable(R.drawable.ic_home_assistant)

    // Home Assistant gets every watch value as a sensor, shows its notifications, changes settings and the face, and
    // runs its chosen action on a button press.
    override val access: PluginAccess = PluginAccess(
        // It maps everything to an entity (declared as everything), so a kind added later is shared
        // too; only the phone's location is left out: no Home Assistant sensor uses it.
        reads = DataUse.ALL - DataUse.PHONE_LOCATION,
        controls = Control.ALL,
        sendsTo = listOf("your Home Assistant server"),
        stores = listOf("Home Assistant address and token", "each watch's registration and the values sent last"),
        // Any watch: battery and connection at least. The rest where the watch has it.
        requires = emptySet(),
        uses = setOf(
            Capability.NOTIFICATIONS, Capability.BATTERY, Capability.TEMPERATURE, Capability.STEPS, Capability.HEALTH_LOG,
            Capability.HEALTH_SETTINGS, Capability.HOME_TIME, Capability.TIMER, Capability.WATCH_SETTINGS,
        ),
    )

    override suspend fun activate(host: PluginHost) = HaRuntime.start(host)

    override suspend fun deactivate() = HaRuntime.stop()

    override suspend fun onEvent(event: WatchEvent) = HaRuntime.onEvent(event)

    override val ui: PluginUi = object : PluginUi {
        @Composable
        override fun SettingsScreen(onBack: () -> Unit) {
            val rt = HaRuntime.active ?: return
            HaScreen(viewModel = viewModel { HaViewModel(rt) }, onBack = onBack)
        }

        override val hasActionsRow: Boolean = true

        @Composable
        override fun ActionsRow(onOpenSettings: () -> Unit) {
            val rt = HaRuntime.active ?: return
            HaButtonActionRow(onOpenSetup = onOpenSettings, viewModel = viewModel { HaViewModel(rt) })
        }
    }

    companion object {
        const val ID = "home_assistant"
        /** Also in build.gradle (HaVersionTest keeps them the same). */
        const val VERSION = "0.1.0"
    }
}

/** What runs while the plugin is on: the host, the store, the sync and the push socket. */
class HaRuntime private constructor(val host: PluginHost) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store = HaStore(PluginKeyValueStore(host.storage()))
    val raw = RawReadSwitch(store.kv)
    val sync = HaSync(host, store, scope, enqueue = { HaWorker.enqueue(host.context) }, extraValues = ::pluginValues)
    val push: HaPushHost = HaPushHost.create(this)

    /** The values only this plugin knows: its push socket. */
    private fun pluginValues(): Map<String, Any?> = mapOf(
        "app.ha_push_connected" to HaPushState.connected,
        "app.ha_push_heartbeat" to java.time.OffsetDateTime.now().withNano(0).toString(),
        "app.ha_push_changed_at" to HaPushState.changedAt.takeIf { it.isNotEmpty() },
    )

    /** What the plugin asks of the host now: the values of the groups that are on, the button, health reads, raw reads; and its status line. */
    fun declareNeeds() {
        host.declareNeeds(needsFor(store.load() != null, store.groupsOn(), raw.isOn()))
        reportStatus()
    }

    /** The line under its name in Settings > Plugins: "Connected", "Not set up", "Last send failed". */
    fun reportStatus() = host.status(PluginStatus.Ok(HaStatus.of(store, host.current()?.key.orEmpty())))

    /** HA's request for a sensor push: the host reads the values (no health read) and sends them here. */
    fun pushSensorsNow() {
        scope.launch {
            val w = host.current() ?: return@launch
            val r = host.command(w, WatchCommand.RefreshValues)
            if (r is PluginResult.Failed && r.reason != FailReason.NOT_CONNECTED) AppLog.log(LogSource.HomeAssistant, "Sensor update not read: ${r.message}")
        }
    }

    /** HA's `command_watch_setting` (or the settings helper): each setting goes to the watch that is current, newest value wins. */
    fun changeSettings(data: Map<String, Any?>) {
        val asked = HaSettingRequest.requested(data)
        if (asked.isEmpty()) { AppLog.log(LogSource.App, "Home Assistant watch setting refused: no setting named"); return }
        scope.launch {
            val w = host.current() ?: run { AppLog.log(LogSource.App, "Home Assistant watch settings not written: no watch"); return@launch }
            for ((key, value) in asked) {
                // Kept until the watch is back when its link is away for a moment (the DW-H5600 drops it often).
                val r = host.command(w, WatchCommand.ChangeSettings(mapOf(key to value)), Delivery.NextConnection(key = "setting.$key"))
                if (r is PluginResult.Failed && r.reason != FailReason.INVALID) AppLog.log(LogSource.App, "Home Assistant set $key: ${r.message}")
            }
        }
    }

    /** A notification from HA to the current watch; during a short drop of the link it waits up to ten minutes. */
    fun deliver(n: WatchNotification) {
        scope.launch {
            val w = host.current() ?: return@launch
            when (val r = host.notify(w, n, Delivery.NextConnection(keepMillis = 10 * 60_000L))) {
                is PluginResult.Ok -> Unit
                is PluginResult.Failed -> AppLog.log(
                    LogSource.HomeAssistant,
                    if (r.reason == FailReason.NOT_SUPPORTED) "Home Assistant notification not sent: the connected watch cannot show notifications"
                    else "Home Assistant notification not sent: ${r.message}",
                )
            }
        }
    }

    suspend fun onEvent(event: WatchEvent) {
        declareNeeds()
        when (event) {
            is WatchEvent.Connected -> {
                val caps = host.watches().firstOrNull { it.id == event.watch }?.capabilities.orEmpty()
                val supported = Capability.NOTIFICATIONS in caps
                store.saveNotificationsSupported(event.watch.key, supported)
                if (supported) { push.acquire(HaPushHost.SESSION); push.watchConnected() }
            }
            is WatchEvent.Disconnected -> push.release(HaPushHost.SESSION)
            WatchEvent.BackgroundStarted -> push.acquire(HaPushHost.SERVICE)
            WatchEvent.BackgroundStopped -> push.release(HaPushHost.SERVICE)
            is WatchEvent.ValuesUpdated -> sync.onValues(event)
            is WatchEvent.ButtonPressed -> sync.onButton(event)
            else -> Unit
        }
    }

    companion object {
        @Volatile var active: HaRuntime? = null
            private set

        /** The store of the last start, also for the worker after the plugin was switched off (it then sends nothing). */
        @Volatile var lastStore: HaStore? = null
            private set

        /**
         * What the plugin asks for. The raw switch on: every raw code (the host then reads them all, a decoding aid);
         * off: no raw code at all, the host reads only what its decoded settings need.
         */
        fun needsFor(configured: Boolean, groups: Set<HaGroup>, rawOn: Boolean): PluginNeeds = PluginNeeds(
            values = if (!configured) emptySet() else (HaSensors.variablesOf(HaGroups.sensors(groups)) - PLUGIN_VALUES).let { v ->
                if (rawOn) v + HaSensors.hostKeys.rawCodes.map { "$RAW$it" } else v.filterNot { it.startsWith(RAW) }.toSet()
            },
            buttonPress = configured,
            // Background health reads only feed Home Assistant's Health group.
            healthReads = configured && HaGroup.HEALTH in groups,
        )

        private const val RAW = "watch.raw."

        /** The values this plugin adds itself. */
        val PLUGIN_VALUES = setOf("app.ha_push_connected", "app.ha_push_heartbeat", "app.ha_push_changed_at")

        fun start(host: PluginHost) {
            // The sensors that list the host's raw codes and writable settings follow what this app has.
            HaSensors.hostKeys = HaSensors.HostKeys.from(
                host.valueCatalog().map { it.key }, host.settingCatalog(host.current() ?: org.avmedia.smartsync.plugin.api.WatchId("")).map { it.key },
            )
            val rt = HaRuntime(host)
            HaSentView.appContext = host.context
            active = rt
            lastStore = rt.store
            rt.declareNeeds()
        }

        fun stop() {
            val rt = active ?: return
            active = null
            rt.push.stop()
            // Switched off: a job that waits is not sent later.
            runCatching { rt.store.pendingIds().forEach(rt.store::clearPending) }
            rt.scope.cancel()
        }

        suspend fun onEvent(event: WatchEvent) { active?.onEvent(event) }
    }
}
