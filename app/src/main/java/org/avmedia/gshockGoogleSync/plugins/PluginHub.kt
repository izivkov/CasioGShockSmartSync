package org.avmedia.gshockGoogleSync.plugins

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.ServiceLoader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.history.HistoryDb
import org.avmedia.gshockGoogleSync.history.HistoryMapper
import org.avmedia.gshockGoogleSync.history.WorkoutStore
import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogSource
import org.avmedia.gshockGoogleSync.session.ConnectionCause
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.session.WatchModelOverride
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.health.H5600Session
import org.avmedia.gshockGoogleSync.ui.time.HomeSnapshotStore
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchSettingsAccess
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.watch.AppStores
import org.avmedia.gshockGoogleSync.watch.CurrentWatch
import org.avmedia.gshockGoogleSync.watch.WatchKeys
import org.avmedia.gshockGoogleSync.watchvalues.RawWatchRequest
import org.avmedia.gshockGoogleSync.watchvalues.SettingType
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingCommands
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingRegistry
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationIds
import org.avmedia.gshockapi.NotificationType
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.smartsync.plugin.api.ConnectionKind
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.HistoryRange
import org.avmedia.smartsync.plugin.api.HistoryRecord
import org.avmedia.smartsync.plugin.api.NotificationKind
import org.avmedia.smartsync.plugin.api.PairedWatch
import org.avmedia.smartsync.plugin.api.SettingInfo
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchNotification
import org.avmedia.smartsync.plugin.api.WatchPlugin

/**
 * The app's side of the plugin interface: finds the plugins (ServiceLoader), starts those switched on, turns the app's
 * events into [WatchEvent]s, reads the watch values for them and runs their writes ([CommandScheduler]). Started from
 * the Application, so it runs without a screen too.
 */
@Singleton
class PluginHub @Inject constructor(
    @param:ApplicationContext val context: Context,
    private val api: GShockRepository,
    features: IWatchFeatureManager,
    raw: RawWatchRequest,
    private val access: WatchSettingsAccess,
    private val sessionManager: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val kv: org.avmedia.gshockGoogleSync.watch.KeyValueStore = AppStores.LocalKeyValueStore(context.applicationContext)
    internal val watchStore = AppStores.watchStore(context)
    val toggles = PluginToggles(kv)
    val appVersion: String = BuildConfig.VERSION_NAME

    val scheduler = CommandScheduler(
        scope,
        connected = { w -> connectedWatch() == w },
        transferRunning = { org.avmedia.gshockapi.io.WatchExchange.transferRunning() },
        log = { id, line -> log(id, line) },
    )

    /** Each plugin's last error and failure count, for its Settings card. */
    val health = PluginHealthBoard()

    val runtime: PluginRuntime = PluginRuntime(
        scope,
        hostFor = { p -> AppPluginHost(this, p) },
        log = ::log,
        onFailed = { id, reason -> toggles.markFailed(id, reason) },
        dropCommands = { id -> scheduler.drop(id) },
        onCaught = { id, text -> health.caught(id, text) },
        onRecovered = { id -> health.callbackOk(id) },
        capsOf = ::capsOf,
    )

    private val writers = java.util.concurrent.CopyOnWriteArrayList<WatchSettingCommands>()
    @Volatile private var backgroundActive = false

    val values = WatchValuesHost(
        registry = lazyRegistry(features, raw),
        store = watchStore,
    )

    private fun lazyRegistry(features: IWatchFeatureManager, raw: RawWatchRequest): () -> org.avmedia.gshockGoogleSync.watchvalues.VariableRegistry {
        val r by lazy { WatchVariables.registry(WatchVariables.androidEnv(context, api, features, raw, rawOn = { runtime.needs().undecodedSettings })) }
        return { r }
    }

    @Volatile private var started = false

    /** Finds and starts the plugins; once per process. */
    fun start() {
        if (started) return
        started = true
        H5600Session.onDownloaded = { scope.launch { onHealthRead() } }
        WorkoutStore.onDownloaded = { scope.launch { onWorkoutStored() } }
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.onSetupDone = { writers.forEach { it.onSetupDone() } }
        runtime.register(load())
        scope.launch {
            switches.startAll()
            AppLog.log(LogSource.App, "Plugins: " + runtime.plugins().joinToString { "${nameOf(it.id)} (${describe(runtime.state(it.id))})" }.ifEmpty { "none" })
        }
    }

    private fun load(): List<WatchPlugin> {
        val out = ArrayList<WatchPlugin>()
        val it = ServiceLoader.load(WatchPlugin::class.java, WatchPlugin::class.java.classLoader).iterator()
        while (true) {
            val next = try { if (!it.hasNext()) break; it.next() } catch (e: Throwable) {
                AppLog.failure(LogSource.App, "A plugin could not be loaded", e); continue
            }
            out += next
        }
        return out
    }

    /** Switches a plugin on or off (Settings, Actions). On again also clears a failure. */
    fun setEnabled(id: String, on: Boolean) {
        if (on) health.clear(id)
        scope.launch { switches.set(id, on) }
    }

    fun isOn(id: String): Boolean = switches.isOn(id)

    /** On, after the person saw what it uses (once). */
    fun accessShown(id: String) = toggles.accessShown(id)
    fun markAccessShown(id: String) = toggles.markAccessShown(id)

    private val switches by lazy {
        PluginSwitchboard(
            toggles, runtime,
            // Home Assistant set up before the plugin interface stays on: its switch was on by default.
            legacyOn = { id -> id == "home_assistant" && kv.get("ha.config") != null },
            afterActivate = { id ->
                // What the plugin missed while it was off: the background service and the watch connected now.
                if (backgroundActive) runtime.dispatchTo(id, WatchEvent.BackgroundStarted)
                connectedWatch()?.let { runtime.dispatchTo(id, WatchEvent.Connected(it, ConnectionKind.USER)) }
            },
        )
    }

    fun describe(state: PluginState): String = when (state) {
        PluginState.Active -> "on"
        PluginState.Off -> "off"
        is PluginState.Failed -> "off: ${state.reason}"
        is PluginState.Incompatible -> "off: needs plugin interface ${state.pluginVersion}"
    }

    /** The names of the plugins that are on and want the watch's button press. */
    fun buttonPluginNames(): List<String> = runtime.plugins().filter { runtime.needsOf(it.id)?.buttonPress == true }.map { nameOf(it.id) }

    fun nameOf(id: String): String = runtime.plugins().firstOrNull { it.id == id }?.let { runCatching { it.name }.getOrNull() } ?: id

    // --- Events from the app ---

    /** A watch session is ready (MainEventHandler). */
    fun onSessionReady(cause: ConnectionCause) {
        val w = connectedWatch() ?: return
        val caps = Capabilities.connected(notifications = runCatching { api.supportsAppNotifications() }.getOrDefault(false))
        runCatching { watchStore.put(w.key, KEY_CAPS, Capabilities.names(caps)) }
        scheduler.onConnected(w)
        runtime.dispatch(WatchEvent.Connected(w, kindOf(cause)))
    }

    fun onSessionClosed(watchKey: String) {
        writers.forEach { it.onDisconnected() }
        if (watchKey.isNotBlank()) runtime.dispatch(WatchEvent.Disconnected(WatchId(watchKey)))
    }

    /** After each connection: the last sync time, then the values. */
    fun onSync() {
        val watch = CurrentWatch.key()
        scope.launch {
            runCatching { watchStore.put(watch, AppStores.KEY_LAST_SYNC, OffsetDateTime.now().withNano(0).toString()) }
            readValues(ValueCause.CONNECTION)
        }
    }

    /** The watch's button asked to run the actions: the values, then the press. */
    fun buttonPressed() {
        scope.launch {
            val ev = readValues(ValueCause.BUTTON) ?: return@launch
            runtime.dispatch(WatchEvent.ButtonPressed(ev.watch, java.time.Instant.now()))
        }
    }

    fun backgroundStarted() { backgroundActive = true; runtime.dispatch(WatchEvent.BackgroundStarted) }

    fun backgroundStopped() { backgroundActive = false; runtime.dispatch(WatchEvent.BackgroundStopped) }

    /** [address] was removed from the app: the plugins' values of that watch go too. */
    fun watchRemoved(address: String) {
        val key = WatchKeys.of(address)
        if (key.isBlank()) return
        runCatching { WatchStorage.deleteAll(kv, key) }
        scheduler.forget(WatchId(key))
        runtime.dispatch(WatchEvent.WatchRemoved(WatchId(key)))
    }

    private suspend fun onHealthRead() {
        val key = CurrentWatch.key().takeIf { it.isNotBlank() } ?: return
        val data = H5600Session.data
        runCatching {
            val batch = HistoryMapper.map(data)
            val dates = (batch.days.map { it.date } + batch.hours.map { it.date } + batch.nights.map { it.date } + batch.hrSlots.map { it.date })
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            val kinds = buildSet<DataKind<*>> {
                if (batch.days.isNotEmpty()) add(DataKind.Days)
                if (batch.hours.isNotEmpty()) add(DataKind.Hours)
                if (batch.hrSlots.isNotEmpty()) add(DataKind.HeartRate)
                if (batch.nights.isNotEmpty()) add(DataKind.Nights)
            }
            val range = HistoryRange.days(dates.minOrNull() ?: LocalDate.now(), dates.maxOrNull() ?: LocalDate.now())
            runtime.dispatch(WatchEvent.HealthRead(WatchId(key), range, kinds))
        }.onFailure { AppLog.failure(LogSource.App, "Plugins: the health read event failed", it) }
        readValues(ValueCause.HEALTH_READ)
    }

    private suspend fun onWorkoutStored() {
        val key = CurrentWatch.key().takeIf { it.isNotBlank() } ?: return
        WorkoutStore.newest(context)?.let { runtime.dispatch(WatchEvent.WorkoutStored(WatchId(key), HistoryRecords.workout(it))) }
        readValues(ValueCause.WORKOUT)
    }

    /**
     * Reads the values the active plugins want of the connected watch and sends them. A watch that is connected is
     * read through the write queue (one exchange at a time); the values that need no watch are read without it.
     */
    suspend fun readValues(cause: ValueCause): WatchEvent.ValuesUpdated? {
        if (runtime.states.value.values.none { it == PluginState.Active }) return null
        val key = CurrentWatch.key().takeIf { it.isNotBlank() } ?: return null
        val w = WatchId(key)
        val wanted = runtime.needs().values
        if (wanted != null && wanted.isEmpty()) return null
        val connected = runCatching { api.isConnected() }.getOrDefault(false)
        var event: WatchEvent.ValuesUpdated? = null
        val read: suspend () -> WriteResult = { event = values.read(w, cause, wanted, connected); CommandScheduler.DONE }
        if (connectedWatch() == w) scheduler.submit(HOST_ID, w, Delivery.Now, "read values", timeoutMs = 120_000, work = read)
        else runCatching { read() }.onFailure { AppLog.failure(LogSource.App, "Reading the watch values failed", it) }
        val ev = event ?: return null
        runtime.dispatch(ev)
        val battery = (ev.values["watch.battery"] as? Number)?.toInt()?.takeIf { "watch.battery" in ev.readNow }
        val temperature = (ev.values["watch.temperature"] as? Number)?.toInt()?.takeIf { "watch.temperature" in ev.readNow }
        if (battery != null || temperature != null) runtime.dispatch(WatchEvent.Condition(w, battery, temperature))
        return ev
    }

    // --- What the hosts ask ---

    /** What [watch] could do at its last connection; empty before its first. */
    fun capsOf(watch: WatchId): Set<org.avmedia.smartsync.plugin.api.Capability> =
        Capabilities.parse(watchStore.get(watch.key, KEY_CAPS)?.value)

    /** For a plugin's page: each paired watch and how far the plugin works with it. */
    fun supportOf(plugin: WatchPlugin): List<Pair<PairedWatch, org.avmedia.smartsync.plugin.api.WatchSupport>> {
        val access = runCatching { plugin.access }.getOrDefault(org.avmedia.smartsync.plugin.api.PluginAccess())
        return watches().map { it to org.avmedia.smartsync.plugin.api.WatchSupport.of(access, it.capabilities) }
    }

    fun connectedWatch(): WatchId? {
        val key = CurrentWatch.key().takeIf { it.isNotBlank() } ?: return null
        val phase = sessionManager.sessionState.value.phase
        return if (phase is SessionPhase.Ready) WatchId(key) else null
    }

    fun currentWatch(): WatchId? = CurrentWatch.keyOrLast(context).takeIf { it.isNotBlank() }?.let(::WatchId)

    fun watches(): List<PairedWatch> {
        val connected = connectedWatch()
        return LocalDataStorage.getDeviceAddresses(context).map { address ->
            val id = WatchId(WatchKeys.of(address))
            PairedWatch(id, watchName(id).orEmpty(), Capabilities.parse(watchStore.get(id.key, KEY_CAPS)?.value), id == connected)
        }
    }

    fun watchName(watch: WatchId): String? {
        if (watch.key == CurrentWatch.key() && WatchInfo.name.isNotBlank()) return WatchInfo.name
        val address = WatchKeys.addressOf(watch.key) ?: return null
        return LocalDataStorage.getDeviceName(context, address)?.takeIf { it.isNotBlank() }
            ?: HomeSnapshotStore(kv).load(address)?.watchName?.takeIf { it.isNotBlank() }
    }

    fun showNotification(watch: WatchId, n: WatchNotification): WriteResult {
        if (connectedWatch() != watch) return CommandScheduler.NOT_CONNECTED
        if (!api.supportsAppNotifications()) {
            return org.avmedia.smartsync.plugin.api.PluginResult.Failed(
                org.avmedia.smartsync.plugin.api.FailReason.NOT_SUPPORTED, "the connected watch cannot show notifications",
            )
        }
        api.sendAppNotification(
            AppNotification.create(type = typeOf(n.kind), timestamp = NotificationIds.timestamp(n.time), app = n.app, title = n.title, text = n.text),
        )
        return CommandScheduler.DONE
    }

    suspend fun writeSettings(host: AppPluginHost, watch: WatchId, values: Map<String, Any?>): WriteResult {
        if (connectedWatch() != watch) return CommandScheduler.NOT_CONNECTED
        return host.settingsWriter.apply(mapOf("settings" to values))
    }

    fun newSettingsWriter(who: String): WatchSettingCommands = WatchSettingCommands(
        write = { s, v -> access.update(s.code, s.touched, { f -> s.change(f, v) }, s.expectedBack, s.matches) },
        connected = { runCatching { api.isConnected() }.getOrDefault(false) },
        afterWrites = { scope.launch { readValues(ValueCause.REQUEST) } },
        log = { AppLog.log(LogSource.ofLabel(who) ?: LogSource.App, it) },
        onQueued = {},
        onWritten = { k, v -> CurrentWatch.key().takeIf { it.isNotBlank() }?.let { runtime.dispatch(WatchEvent.SettingChanged(WatchId(it), k, v)) } },
        watch = { CurrentWatch.key() },
        watchName = { WatchInfo.name },
        who = who,
    ).also { writers += it }

    fun settingCatalog(watch: WatchId): List<SettingInfo> {
        val name = watchName(watch).orEmpty()
        return WatchSettingRegistry.ALL.map { s ->
            when (val t = s.type) {
                is SettingType.Choice -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.CHOICE, choices = s.valuesFor?.invoke(name) ?: t.values)
                is SettingType.Whole -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.WHOLE, range = t.range, only = t.only)
                SettingType.Bool -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.BOOL)
                SettingType.Time -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.TIME)
            }
        }
    }

    suspend fun <T : HistoryRecord> history(watch: WatchId, kind: DataKind<T>, range: HistoryRange): List<T> = withContext(Dispatchers.IO) {
        val dao = HistoryDb.forWatch(context, watch.key).dao()
        HistoryRecords.read(object : HistoryRecords.Rows {
            override fun days() = dao.allDays()
            override fun hours() = dao.allHours()
            override fun hrSlots(date: String) = dao.hrSlotsOf(date)
            override fun nights() = dao.allNights()
            override fun workouts() = dao.allWorkouts()
        }, kind, range)
    }

    /** The same watch, re-paired: every per-watch value (the app's and every plugin's) and its home screen values move. */
    fun moveWatchData(from: WatchId, to: WatchId) {
        if (from == to) return
        runCatching { watchStore.move(from.key, to.key) }
        val a = WatchKeys.addressOf(from.key)
        val b = WatchKeys.addressOf(to.key)
        if (a != null && b != null) runCatching { HomeSnapshotStore(kv).move(a, b) }
    }

    /** A line about plugin [id] (the host's own: start, failures), tagged with the plugin's id like the plugin's lines. */
    fun log(id: String, line: String) {
        timber.log.Timber.tag(id).i(line)
    }

    fun failure(id: String, line: String, error: Throwable) {
        timber.log.Timber.tag(id).w("$line (${error.javaClass.simpleName})")
    }

    companion object {
        const val HOST_ID = "app"
        const val KEY_CAPS = "plugins.caps"

        fun kindOf(cause: ConnectionCause): ConnectionKind = when (cause) {
            ConnectionCause.USER -> ConnectionKind.USER
            ConnectionCause.WATCH -> ConnectionKind.WATCH
            ConnectionCause.BACKGROUND_READ -> ConnectionKind.BACKGROUND_READ
        }

        fun typeOf(kind: NotificationKind): NotificationType = when (kind) {
            NotificationKind.MESSAGE -> NotificationType.MESSAGE
            NotificationKind.CALENDAR -> NotificationType.CALENDAR
            NotificationKind.EMAIL -> NotificationType.EMAIL
            NotificationKind.CALL -> NotificationType.PHONE_CALL
            NotificationKind.GENERIC -> NotificationType.GENERIC
        }
    }
}
