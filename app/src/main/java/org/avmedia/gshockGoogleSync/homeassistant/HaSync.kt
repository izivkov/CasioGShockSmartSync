package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogSource
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.health.H5600Session
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import timber.log.Timber

/** What caused a Home Assistant push. [eventName] is the `trigger` of the button event and the `event` variable. */
enum class HaTrigger(val eventName: String) {
    /** The watch asked the app to run its actions (button press, Phone Finder on always-connected watches). */
    WATCH_ACTION("watch_action"),

    /** After each watch connection / sync. */
    SYNC("sync"),

    /** A DW-H5600 LifeLog read was stored. */
    HEALTH_DATA("health_data"),

    /** A DW-H5600 workout was downloaded and stored. */
    WORKOUT("workout_downloaded"),
}

/** The job one push queues. Pure, so the button press is tested without Android. */
object HaJobs {
    /** The button event: only a watch action has one; HA runs the chosen action after it ([HaClient]). */
    fun event(trigger: HaTrigger, now: OffsetDateTime, watchName: String): Map<String, Any?>? =
        if (trigger == HaTrigger.WATCH_ACTION) mapOf("trigger" to trigger.eventName, "time" to now.toString(), "watch" to watchName)
        else null

    /**
     * The registration body for the watch at [address], with the name the owner confirmed. Null for a watch that is
     * not registered yet and has no confirmed name: nothing may register before the owner confirmed it.
     */
    fun registration(
        store: HaStore, address: String, watchName: String, cfg: HaConfig, appVersion: String, osVersion: String,
        carryOver: HaCarryOver? = null,
    ): String? {
        val id = HaPayloads.deviceId(address)
        if (store.confirmedName(id) == null && store.loadRegistration(id) == null &&
            carryOver?.moveTo(store, address, watchName, cfg) != true
        ) {
            carryOver?.skipped(id, "$watchName not synced: no registration for this watch; confirm its name on the Home Assistant screen")
            return null
        }
        val confirmed = store.confirmedName(id)
        // The model is this watch's own (typed for it, or the one it registered with), never another watch's.
        return HaPayloads.registrationFor(address, watchName, cfg.copy(deviceName = confirmed ?: cfg.deviceName, model = store.modelFor(id)), appVersion, osVersion)
    }

    /** Null when there is nothing to send (no sensor values and no event). */
    fun job(
        trigger: HaTrigger,
        now: OffsetDateTime,
        watchName: String,
        states: Map<String, Any>,
        attributes: Map<String, Map<String, Any?>>,
        deviceId: String,
        registration: String,
        h5600: Boolean? = null,
        caps: Set<WatchCap>? = null,
    ): HaJob? {
        val event = event(trigger, now, watchName)
        if (states.isEmpty() && event == null) return null
        return HaJob(states, event, attributes, deviceId, registration, h5600, caps?.let(WatchCaps::names))
    }
}

/**
 * Sends the watch's values to Home Assistant. Callers: the button action (trigger watch_action), the connection
 * runner (sync), the LifeLog read (health_data) and a stored workout (workout_downloaded).
 */
@Singleton
class HaSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
    features: IWatchFeatureManager,
    raw: RawWatchRequest,
    access: org.avmedia.gshockGoogleSync.ui.watchhealth.WatchSettingsAccess,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // The point where a DW-H5600 LifeLog read completes.
        H5600Session.onDownloaded = { fire(HaTrigger.HEALTH_DATA) }
        // A stored workout pushes the sensors to Home Assistant.
        org.avmedia.gshockGoogleSync.history.WorkoutStore.onDownloaded = { fire(HaTrigger.WORKOUT) }
        // Home Assistant's "command_update_sensors" push: send the sensors once, nothing else.
        HaPushRouter.updateSensorsHook = { pushSensorsNow() }
        // Home Assistant's "command_watch_setting": checked, queued, written once the watch's setup is done.
        val commands = WatchSettingCommands(
            write = { s, v -> access.update(s.code, s.touched, { f -> s.change(f, v) }, s.expectedBack, s.matches) },
            connected = { runCatching { api.isConnected() }.getOrDefault(false) },
            afterWrites = { pushSensorsNow() },
            watch = { org.avmedia.gshockGoogleSync.watch.CurrentWatch.key() },
            watchName = { org.avmedia.gshockapi.WatchInfo.name },
        )
        WatchSettingCommands.shared = commands
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.onSetupDone = { commands.onSetupDone() }
    }

    // A re-paired watch has a new address: its registration and home screen values move to it.
    private val carryOver = run {
        val snapshots = org.avmedia.gshockGoogleSync.ui.time.HomeSnapshotStore(AppStores.LocalKeyValueStore(context))
        HaCarryOver(
            paired = { a -> LocalDataStorage.getDeviceAddresses(context).any { it.equals(a, ignoreCase = true) } },
            watchNameAt = { a -> snapshots.load(a)?.watchName },
            moveOthers = { from, to -> snapshots.move(from, to) },
            log = { AppLog.log(LogSource.HomeAssistant, it) },
        )
    }

    val registry: VariableRegistry = WatchVariables.registry(WatchVariables.androidEnv(context, api, features, raw))

    /** Records "now" as the last connection, then pushes the sensors. */
    fun onSync() {
        val watch = org.avmedia.gshockGoogleSync.watch.CurrentWatch.key()
        scope.launch {
            runCatching { AppStores.watchStore(context).put(watch, AppStores.KEY_LAST_SYNC, OffsetDateTime.now().withNano(0).toString()) }
            fire(HaTrigger.SYNC)
        }
    }

    /** One Home Assistant sensor push on request of HA. Uses the values already known: no LifeLog read is started. */
    fun pushSensorsNow() = fire(HaTrigger.SYNC, logFailure = true)

    fun fire(trigger: HaTrigger, logFailure: Boolean = false) {
        if (!AppStores.toggles(context).isOn(ServiceToggles.Service.HOME_ASSISTANT)) return
        scope.launch {
            runCatching { push(trigger) }.onFailure {
                Timber.e(it, "Home Assistant push failed")
                if (logFailure) AppLog.failure(LogSource.HomeAssistant, "Push failed", it)
            }
        }
    }

    /** The sensor values (one registry read) and, for a watch action, the button event. */
    private suspend fun push(trigger: HaTrigger) {
        val store = AppStores.haStore(context)
        val cfg = store.load() ?: return
        val address = org.avmedia.gshockapi.WatchInfo.getAddress()
        if (address.isBlank()) return
        val watchName = org.avmedia.gshockapi.WatchInfo.name
        val now = OffsetDateTime.now().withNano(0)
        val ctx = VariableContext(trigger.eventName, now, runCatching { api.isConnected() }.getOrDefault(false))
        val caps = WatchCaps.connected()
        val h5600 = WatchCap.H5600 in caps
        // Only what this model has: an OCW registers and sends no health, workout or settings sensor.
        val sensors = HaSensors.forCaps(HaGroups.sensors(store.groupsOn()), caps)
        val values = registry.resolve(ctx, HaSensors.variablesOf(sensors))
        val registration = HaJobs.registration(
            store, address, watchName, cfg, org.avmedia.gshockGoogleSync.BuildConfig.VERSION_NAME, android.os.Build.VERSION.RELEASE,
            carryOver,
        ) ?: run {
            store.saveResult(HaPayloads.deviceId(address), HaClient.NAME_FIRST, false)
            return
        }
        val job = HaJobs.job(
            trigger, now, watchName, HaSensors.states(values, sensors), HaSensors.attributes(values, sensors),
            HaPayloads.deviceId(address), registration, h5600, caps,
        ) ?: return
        store.putPending(job)
        HaWorker.enqueue(context)
    }
}
