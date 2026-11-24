package org.avmedia.smartsync.homeassistant

import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId

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
 * Sends the watch's values to Home Assistant: after each connection (sync), a health read (health_data), a stored
 * workout (workout_downloaded) and a button press (watch_action, with the button event). The values come from the host
 * ([WatchEvent.ValuesUpdated]); only what was read now is sent.
 */
class HaSync(
    private val host: PluginHost,
    private val store: HaStore,
    private val scope: CoroutineScope,
    private val enqueue: () -> Unit,
    /** The values only this plugin has (its push socket). */
    private val extraValues: () -> Map<String, Any?> = { emptyMap() },
    private val appVersion: () -> String = { host.appVersion },
    private val osVersion: () -> String = { android.os.Build.VERSION.RELEASE.orEmpty() },
) {
    // A re-paired watch has a new address: its registration and the host's per-watch values move to it.
    private val carryOver = HaCarryOver(
        paired = { a -> host.watches().any { it.id.key == HaPayloads.deviceId(a) } },
        watchNameAt = { a -> host.watchName(WatchId(HaPayloads.deviceId(a))) },
        moveOthers = { from, to -> host.moveWatchData(WatchId(HaPayloads.deviceId(from)), WatchId(HaPayloads.deviceId(to))) },
        log = { AppLog.log(LogSource.HomeAssistant, it) },
    )

    /** The values read with a button press, for the press that follows them. */
    private val buttonValues = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any?>>()

    fun onValues(ev: WatchEvent.ValuesUpdated) {
        if (ev.cause == ValueCause.BUTTON) { buttonValues[ev.watch.key] = ev.fresh(); return }
        val trigger = when (ev.cause) {
            ValueCause.HEALTH_READ -> HaTrigger.HEALTH_DATA
            ValueCause.WORKOUT -> HaTrigger.WORKOUT
            else -> HaTrigger.SYNC
        }
        fire(trigger, ev.watch, ev.fresh(), logFailure = ev.cause == ValueCause.REQUEST)
    }

    fun onButton(ev: WatchEvent.ButtonPressed) {
        val values = buttonValues.remove(ev.watch.key) ?: host.lastValues(ev.watch).values
        fire(HaTrigger.WATCH_ACTION, ev.watch, values)
    }

    fun fire(trigger: HaTrigger, watch: WatchId, values: Map<String, Any?>, logFailure: Boolean = false) {
        scope.launch {
            runCatching { push(trigger, watch, values) }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                AppLog.debug("Home Assistant push failed: ${it.javaClass.simpleName}")
                if (logFailure) AppLog.failure(LogSource.HomeAssistant, "Push failed", it)
            }
        }
    }

    /** The sensor values (states and attributes) and, for a watch action, the button event. */
    internal fun push(trigger: HaTrigger, watch: WatchId, read: Map<String, Any?>) {
        val cfg = store.load() ?: return
        val address = HaCarryOver.addressOf(watch.key) ?: return
        val paired = host.watches().firstOrNull { it.id == watch }
        val watchName = host.watchName(watch) ?: paired?.name.orEmpty()
        val now = OffsetDateTime.now().withNano(0)
        val caps = WatchCaps.of(paired?.capabilities.orEmpty())
        val h5600 = WatchCap.H5600 in caps
        // Only what this model has: an OCW registers and sends no health, workout or settings sensor.
        val sensors = HaSensors.forCaps(HaGroups.sensors(store.groupsOn()), caps)
        val values = read + extraValues()
        val registration = HaJobs.registration(store, address, watchName, cfg, appVersion(), osVersion(), carryOver) ?: run {
            store.saveResult(HaPayloads.deviceId(address), HaClient.NAME_FIRST, false)
            return
        }
        val job = HaJobs.job(
            trigger, now, watchName, HaSensors.states(values, sensors), HaSensors.attributes(values, sensors),
            HaPayloads.deviceId(address), registration, h5600, caps,
        ) ?: return
        store.putPending(job)
        enqueue()
    }
}
