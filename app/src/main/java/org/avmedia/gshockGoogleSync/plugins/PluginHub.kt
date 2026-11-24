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
import org.avmedia.gshockGoogleSync.watchvalues.RawSettings
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

    /** Each plugin's status line, last error and failure count, for its line in Settings > Plugins. */
    val health = PluginHealthBoard()

    val values = WatchValuesHost(
        registry = lazyRegistry(features, raw),
        store = watchStore,
    )

    val runtime: PluginRuntime = PluginRuntime(
        scope,
        hostFor = { p -> AppPluginHost(this, p, runtime.ids().firstOrNull { runtime.plugin(it) === p } ?: p.id) },
        log = ::log,
        onFailed = { id, reason -> toggles.markFailed(id, reason) },
        dropCommands = { id -> scheduler.drop(id) },
        onCaught = { id, text -> health.caught(id, text) },
        onRecovered = { id -> health.callbackOk(id) },
        capsOf = ::capsOf,
        uses = { key -> values.useOf(key) },
        allKeys = { values.keys() },
        settingKeys = { WatchSettingRegistry.ALL.map { org.avmedia.gshockGoogleSync.watchvalues.WatchSettingVariables.valueKey(it.key) } },
    )

    /** What each plugin misses, kept as state: checked off the main thread ([PluginPermissionCache]). */
    val permissions = PluginPermissionCache(
        scope,
        access = { id -> runtime.plugin(id)?.let { p -> runCatching { p.access }.getOrNull() } },
        granted = { name -> context.checkSelfPermission(name) == android.content.pm.PackageManager.PERMISSION_GRANTED },
        ownCheck = { id -> runtime.plugin(id)?.missingPermissions(context) },
    )

    private val writers = java.util.concurrent.CopyOnWriteArrayList<WatchSettingCommands>()
    @Volatile private var backgroundActive = false
    /** How the connected watch connected (for the Connected event a plugin that starts later gets). */
    @Volatile private var lastKind: ConnectionKind = ConnectionKind.USER

    private fun lazyRegistry(features: IWatchFeatureManager, raw: RawWatchRequest): () -> org.avmedia.gshockGoogleSync.watchvalues.VariableRegistry {
        // Every raw code is read only while a plugin asks for a raw value beyond the decoded ones (a decoding aid).
        val r by lazy { WatchVariables.registry(WatchVariables.androidEnv(context, api, features, raw, rawOn = { RawSettings.wantsAll(runtime.needs().values) })) }
        return { r }
    }

    @Volatile private var started = false

    /** Done once the plugins switched on have been started (a wake-up job waits for it). */
    val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

    /**
     * The one place events leave for the plugins: every source posts here and one coroutine runs them in order, so the
     * events of a watch come in the order the app saw them.
     */
    private val events = SerialEvents(scope) { AppLog.failure(LogSource.App, "Plugins: an event failed", it) }

    private fun post(work: suspend () -> Unit) = events.post(work)

    /** Finds and starts the plugins; once per process. */
    fun start() {
        if (started) return
        started = true
        H5600Session.onDownloaded = { post { onHealthRead() } }
        WorkoutStore.onDownloaded = { post { onWorkoutStored() } }
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.onSetupDone = { writers.forEach { it.onSetupDone() } }
        runtime.register(load())
        scope.launch {
            try {
                switches.startAll()
                refreshPermissions()
                AppLog.log(LogSource.App, "Plugins: " + runtime.ids().joinToString { "${nameOf(it)} (${describe(runtime.state(it))})" }.ifEmpty { "none" })
                leftOverPlugins().takeIf { it.isNotEmpty() }?.let { AppLog.log(LogSource.App, "Plugins: data of removed plugins: ${it.joinToString()}") }
                scheduleWake()
            } finally {
                ready.complete(Unit)
            }
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

    /** Switch changes run one after the other, in the order of the taps: the last tap wins. */
    private val switchChanges = kotlinx.coroutines.channels.Channel<Pair<String, Boolean>>(kotlinx.coroutines.channels.Channel.UNLIMITED).also { ch ->
        scope.launch { for ((id, on) in ch) runCatching { switches.set(id, on) }.onFailure { AppLog.failure(LogSource.App, "Plugins: switching $id failed", it) } }
    }

    /** Switches a plugin on or off (Settings, Actions). On again also clears a failure. */
    fun setEnabled(id: String, on: Boolean) {
        if (on) health.clear(id)
        switchChanges.trySend(id to on)
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
                // What the plugin missed while it was off: the background service, the watch connected now and its values.
                if (backgroundActive) runtime.dispatchTo(id, WatchEvent.BackgroundStarted)
                connectedWatch()?.let { w ->
                    runtime.dispatchTo(id, WatchEvent.Connected(w, lastKind))
                    post { readValues(ValueCause.CONNECTION) }
                }
                scheduleWake()
            },
            // Turned on by the person: what it needs and does not have yet is asked for now.
            afterTurnOn = { id -> requestPermissions(id) },
            opensOnTurnOn = { id -> runtime.plugin(id)?.ui?.opensOnTurnOn == true },
            open = { id -> _toOpen.tryEmit(id) },
        )
    }

    private val _toOpen = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** A plugin's screen to open now: it asked for it at turn-on ([PluginSwitchboard]), from whichever switch. */
    val toOpen: kotlinx.coroutines.flow.SharedFlow<String> = _toOpen

    fun describe(state: PluginState): String = when (state) {
        PluginState.Active -> "on"
        PluginState.Off -> "off"
        is PluginState.Failed -> "off: ${state.reason}"
        is PluginState.Incompatible -> "off: needs plugin interface ${state.pluginVersion}"
    }

    /** The names of the plugins that are on and want the watch's button press. */
    fun buttonPluginNames(): List<String> = runtime.ids().filter { runtime.needsOf(it)?.buttonPress == true }.map(::nameOf)

    fun nameOf(id: String): String = runtime.plugin(id)?.let { runCatching { it.name }.getOrNull() } ?: id

    /** A plugin's needs changed: the union decides the wake-up job. */
    fun declareNeeds(id: String, needs: org.avmedia.smartsync.plugin.api.PluginNeeds) {
        val before = runtime.needs().wakeEveryMinutes
        runtime.declareNeeds(id, needs)
        if (runtime.needs().wakeEveryMinutes != before) scheduleWake()
    }

    private fun scheduleWake() = runCatching { PluginWakeWorker.schedule(context, runtime.needs().wakeEveryMinutes) }
        .onFailure { AppLog.failure(LogSource.App, "Plugins: the wake-up job could not be set", it) }

    /** A permission request for the app's screen: [permissions] at runtime, or the plugin's own [intent]. */
    class PermissionRequest(val pluginId: String, val permissions: List<String>, val intent: android.content.Intent?)

    private val _permissionRequests = kotlinx.coroutines.flow.MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 8)

    /** Collected by the app's activity, which asks the person and calls [permissionsAnswered]. */
    val permissionRequests: kotlinx.coroutines.flow.SharedFlow<PermissionRequest> = _permissionRequests

    /** The permissions of plugin [id] missing at the last check; never waits ([permissions]). */
    fun missingPermissions(id: String): List<org.avmedia.smartsync.plugin.api.AndroidPermission> = permissions.of(id)

    /** Checks every plugin's permissions again (start, the app back in front). */
    fun refreshPermissions() { runtime.ids().forEach { permissions.refresh(it) } }

    /** Asks for what plugin [id] misses, through the app's screen; nothing when it misses nothing. */
    fun requestPermissions(id: String) {
        scope.launch {
            val missing = permissions.check(id)
            if (missing.isEmpty()) return@launch
            val intent = runtime.plugin(id)?.let { p -> runCatching { p.permissionIntent(context) }.getOrNull() }
            _permissionRequests.tryEmit(PermissionRequest(id, missing.map { it.name }, intent))
        }
    }

    /** The person answered: the plugin learns what is still missing. */
    fun permissionsAnswered(id: String) {
        scope.launch {
            val missing = permissions.check(id).map { it.name }.toSet()
            log(id, if (missing.isEmpty()) "permissions granted" else "permissions still missing: ${missing.joinToString { it.substringAfterLast('.') }}")
            post { runtime.dispatchTo(id, WatchEvent.PermissionsChanged(missing)) }
        }
    }

    /** The periodic job ran: [WatchEvent.Wake] for the plugins whose interval passed, and they handled it (60 s at most). */
    suspend fun wakeAndWait(): List<String> = runtime.wakeAndWait(System.currentTimeMillis())

    // --- Events from the app ---

    /** A watch session is ready (MainEventHandler). */
    fun onSessionReady(cause: ConnectionCause) {
        val w = connectedWatch() ?: return
        val caps = Capabilities.connected(notifications = runCatching { api.supportsAppNotifications() }.getOrDefault(false))
        val name = WatchInfo.name
        lastKind = kindOf(cause)
        val kind = lastKind
        post {
            rePaired(w, name)
            runCatching { watchStore.put(w.key, KEY_CAPS, Capabilities.names(caps)) }
            scheduler.onConnected(w)
            runtime.dispatch(WatchEvent.Connected(w, kind))
        }
    }

    /** A watch the app never saw: the same watch under its old id when one fits ([RePair]); its stores move. */
    private fun rePaired(w: WatchId, name: String) {
        if (watchStore.get(w.key, KEY_CAPS) != null) return
        val known = PluginWatchData.known(kv)
        val paired = LocalDataStorage.getDeviceAddresses(context).map(WatchKeys::of).toSet()
        val from = RePair.find(w.key, name, known, paired) { watchName(WatchId(it)) } ?: return
        moveWatchData(WatchId(from), w)
        AppLog.log(LogSource.App, "Plugins: $name re-paired, its data moved to the new address")
        runtime.dispatch(WatchEvent.WatchRePaired(WatchId(from), w))
    }

    fun onSessionClosed(watchKey: String) {
        writers.forEach { it.onDisconnected() }
        if (watchKey.isNotBlank()) post { runtime.dispatch(WatchEvent.Disconnected(WatchId(watchKey))) }
    }

    /** After each connection: the last sync time, then the values. */
    fun onSync() {
        val watch = CurrentWatch.key()
        post {
            runCatching { watchStore.put(watch, AppStores.KEY_LAST_SYNC, OffsetDateTime.now().withNano(0).toString()) }
            readValues(ValueCause.CONNECTION)
        }
    }

    /** The watch's button asked to run the actions: the values, then the press (also when no value could be read). */
    fun buttonPressed() {
        post {
            val ev = readValues(ValueCause.BUTTON)
            val w = pressTarget(ev, CurrentWatch.key()) ?: return@post
            runtime.dispatch(WatchEvent.ButtonPressed(w, java.time.Instant.now()))
        }
    }

    fun backgroundStarted() { backgroundActive = true; post { runtime.dispatch(WatchEvent.BackgroundStarted) } }

    fun backgroundStopped() { backgroundActive = false; post { runtime.dispatch(WatchEvent.BackgroundStopped) } }

    /**
     * [address] was removed from the app: the plugins' values of that watch go too, and so do the host's own (its
     * capabilities and kept values), so a removed watch is never taken for a re-paired one ([RePair]).
     */
    fun watchRemoved(address: String) {
        val key = WatchKeys.of(address)
        if (key.isBlank()) return
        runCatching { PluginWatchData.remove(kv, key) }
        scheduler.forget(WatchId(key))
        post { runtime.dispatch(WatchEvent.WatchRemoved(WatchId(key))) }
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

    private val readLock = Any()
    private var pendingRead: kotlinx.coroutines.CompletableDeferred<WriteResult>? = null

    /** A plugin's [org.avmedia.smartsync.plugin.api.WatchCommand.RefreshValues]: joins a read already asked for, and gives its result. */
    suspend fun refreshValues(): WriteResult {
        val (d, mine) = synchronized(readLock) {
            pendingRead?.let { it to false } ?: kotlinx.coroutines.CompletableDeferred<WriteResult>().also { pendingRead = it } to true
        }
        if (mine) post {
            var r: WriteResult = CommandScheduler.NOT_CONNECTED
            try { r = readValuesResult(ValueCause.REQUEST).second } finally {
                synchronized(readLock) { if (pendingRead === d) pendingRead = null }
                d.complete(r)
            }
        }
        return d.await()
    }

    /** Reads the values the active plugins want and sends them; null when nothing was read. */
    suspend fun readValues(cause: ValueCause): WatchEvent.ValuesUpdated? = readValuesResult(cause).first

    /**
     * Reads the values the active plugins want of the connected watch and sends them, with the read's result. A watch
     * that is connected is read through the write queue (one exchange at a time); the values that need no watch are
     * read without it.
     */
    private suspend fun readValuesResult(cause: ValueCause): Pair<WatchEvent.ValuesUpdated?, WriteResult> {
        if (runtime.states.value.values.none { it == PluginState.Active }) return null to CommandScheduler.DONE
        val key = CurrentWatch.key().takeIf { it.isNotBlank() } ?: return null to CommandScheduler.NOT_CONNECTED
        val w = WatchId(key)
        val wanted = runtime.needs().values
        if (wanted != null && wanted.isEmpty()) return null to CommandScheduler.DONE
        val connected = runCatching { api.isConnected() }.getOrDefault(false)
        var event: WatchEvent.ValuesUpdated? = null
        val read: suspend () -> WriteResult = { event = values.read(w, cause, wanted, connected); CommandScheduler.DONE }
        val result = if (connectedWatch() == w) scheduler.submit(CommandScheduler.HOST, w, Delivery.Now, "read values", timeoutMs = 120_000, work = read)
        else runCatching { read() }.getOrElse {
            AppLog.failure(LogSource.App, "Reading the watch values failed", it)
            org.avmedia.smartsync.plugin.api.PluginResult.Failed(org.avmedia.smartsync.plugin.api.FailReason.INTERNAL, it.javaClass.simpleName)
        }
        val ev = event ?: return null to result
        runtime.dispatch(ev)
        return ev to result
    }

    // --- What the hosts ask ---

    /** What [watch] could do at its last connection; empty before its first. */
    fun capsOf(watch: WatchId): Set<org.avmedia.smartsync.plugin.api.Capability> =
        Capabilities.parse(watchStore.get(watch.key, KEY_CAPS)?.value)

    /** For a plugin's page: each paired watch and how far the plugin works with it. */
    fun supportOf(plugin: WatchPlugin): List<Pair<PairedWatch, WatchSupport>> {
        val access = runCatching { plugin.access }.getOrDefault(org.avmedia.smartsync.plugin.api.PluginAccess())
        return watches().map { it to WatchSupport.of(access, it.capabilities) }
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
        api.sendAppNotification(toAppNotification(n))
        return CommandScheduler.DONE
    }

    suspend fun writeSettings(host: AppPluginHost, watch: WatchId, values: Map<String, Any?>): WriteResult {
        if (connectedWatch() != watch) return CommandScheduler.NOT_CONNECTED
        return host.settingsWriter.apply(mapOf("settings" to values))
    }

    fun newSettingsWriter(who: String): WatchSettingCommands = WatchSettingCommands(
        write = { s, v -> access.update(s.code, s.touched, { f -> s.change(f, v) }, s.expectedBack, s.matches) },
        connected = { runCatching { api.isConnected() }.getOrDefault(false) },
        afterWrites = { post { readValues(ValueCause.REQUEST) } },
        log = { AppLog.log(LogSource.ofLabel(who) ?: LogSource.App, it) },
        onQueued = {},
        onWritten = { k, v -> CurrentWatch.key().takeIf { it.isNotBlank() }?.let { w -> post { runtime.dispatch(WatchEvent.SettingChanged(WatchId(w), k, v)) } } },
        watch = { CurrentWatch.key() },
        watchName = { WatchInfo.name },
        who = who,
    ).also { writers += it }

    /** The settings a plugin may write on [watch]: only those its capabilities have (all for a watch not seen yet). */
    fun settingCatalog(watch: WatchId): List<SettingInfo> = settingCatalog(capsOf(watch), watchName(watch).orEmpty())

    /** Does the app know [watch] (paired now, or seen at a connection)? */
    fun knowsWatch(watch: WatchId): Boolean =
        watch == connectedWatch() || watches().any { it.id == watch } || watchStore.get(watch.key, KEY_CAPS) != null

    fun removeSettingsWriter(w: WatchSettingCommands) { writers.remove(w) }

    /** Deletes every key of plugin [id] (Settings > Plugins, only while it is off); true when it was off. */
    fun clearData(id: String): Boolean {
        if (runtime.state(id) == PluginState.Active) return false
        val keys = kv.keys().filter { PluginNamespaces.ownsKey(id, it) }
        runCatching { kv.putAll(keys.associateWith { null }) }
        health.clear(id)
        AppLog.log(LogSource.App, "Plugins: data of ${nameOf(id)} deleted (${keys.size} keys)")
        return true
    }

    /** Ids of plugins no longer in this app that still have data here. */
    fun leftOverPlugins(): Set<String> = runCatching { PluginNamespaces.leftOver(kv.keys(), runtime.ids().toSet()) }.getOrDefault(emptySet())

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
    private fun moveWatchData(from: WatchId, to: WatchId) {
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
        const val KEY_CAPS = "plugins.caps"

        /** The watch a button press is for: the watch whose values were read, else the current one (the press is never lost). */
        fun pressTarget(read: WatchEvent.ValuesUpdated?, currentKey: String): WatchId? =
            read?.watch ?: currentKey.takeIf { it.isNotBlank() }?.let(::WatchId)

        /** The catalogue for a watch with [caps] named [watchName]: the health settings rows only where it has them. */
        fun settingCatalog(caps: Set<org.avmedia.smartsync.plugin.api.Capability>, watchName: String): List<SettingInfo> {
            if (caps.isNotEmpty() && org.avmedia.smartsync.plugin.api.Capability.HEALTH_SETTINGS !in caps) return emptyList()
            return WatchSettingRegistry.ALL.map { s -> settingInfo(s, watchName) }
        }

        /** A catalogue row as the interface names it: type, choices for [watchName], label and control. */
        fun settingInfo(s: org.avmedia.gshockGoogleSync.watchvalues.WatchSetting, watchName: String): SettingInfo {
            val label = WatchSettingRegistry.label(s.key)
            val control = WatchSettingRegistry.controlOf(s.key)
            return when (val t = s.type) {
                is SettingType.Choice -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.CHOICE, choices = s.valuesFor?.invoke(watchName) ?: t.values, label = label, control = control)
                is SettingType.Whole -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.WHOLE, range = t.range, only = t.only, label = label, control = control)
                SettingType.Bool -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.BOOL, label = label, control = control)
                SettingType.Time -> SettingInfo(s.key, org.avmedia.smartsync.plugin.api.SettingType.TIME, label = label, control = control)
            }
        }

        fun kindOf(cause: ConnectionCause): ConnectionKind = when (cause) {
            ConnectionCause.USER -> ConnectionKind.USER
            ConnectionCause.WATCH -> ConnectionKind.WATCH
            ConnectionCause.BACKGROUND_READ -> ConnectionKind.BACKGROUND_READ
        }

        /** A plugin's notification as the library sends it. */
        fun toAppNotification(n: WatchNotification): AppNotification =
            AppNotification.create(type = typeOf(n.kind), timestamp = NotificationIds.timestamp(n.time), app = n.app, title = n.title, text = n.text)

        fun typeOf(kind: NotificationKind): NotificationType = when (kind) {
            NotificationKind.MESSAGE -> NotificationType.MESSAGE
            NotificationKind.CALENDAR -> NotificationType.CALENDAR
            NotificationKind.EMAIL -> NotificationType.EMAIL
            NotificationKind.CALL -> NotificationType.PHONE_CALL
            NotificationKind.GENERIC -> NotificationType.GENERIC
        }
    }
}
