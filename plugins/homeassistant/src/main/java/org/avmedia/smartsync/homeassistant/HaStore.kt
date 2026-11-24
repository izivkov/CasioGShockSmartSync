package org.avmedia.smartsync.homeassistant

import androidx.annotation.Keep
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy

/** What a send carries: the sensor states (id to value) and, for a watch action, the button event. */
@Keep
data class HaJob(
    val states: Map<String, Any> = emptyMap(),
    val event: Map<String, Any?>? = null,
    /** Sensor id to attributes. Null in a job stored by an older version. */
    val attributes: Map<String, Map<String, Any?>>? = null,
    /** The watch this job is for ([HaPayloads.deviceId]) and the registration body to use for it. */
    val deviceId: String = "",
    val registration: String = "",
    /** True for the DW-H5600 family: only then are its own sensors (sleep, cardio, workouts, ...) registered. Null in an older job = all. */
    val h5600: Boolean? = null,
    /** What the watch has ([WatchCaps.names]); its sensors only. Null in an older job: [h5600] decides. */
    val caps: List<String>? = null,
)

/** The Home Assistant setup, entered once: where HA is, how to get in, and the optional name and model typed by the owner. */
@Keep
data class HaConfig(
    val baseUrl: String = "",
    val token: String = "",
    /** Typed in the form; empty = the default from the watch name. */
    val deviceName: String = "",
    val model: String = "",
)

/** One watch's registration in HA, kept per watch address (device id). */
@Keep
data class HaRegistration(
    val webhookId: String = "",
    /** The registration body, kept so a deleted registration can be made again and a changed name noticed. */
    val registration: String = "",
    /** [HaSensors.fingerprint] of the sensors HA knows. Empty = register them before the next update. */
    val sensorsHash: String = "",
)

/**
 * Sensor values last sent to HA, their attributes and the time (epoch millis, 0 = never). [attributes] is null in a
 * value stored by an older version.
 */
@Keep
data class HaSent(
    val states: Map<String, Any> = emptyMap(),
    val at: Long = 0,
    val attributes: Map<String, Map<String, Any?>>? = null,
)

/**
 * The Home Assistant setup, one registration per watch and, per watch ([WatchStore]), the newest unsent job, the values
 * last sent, the last result and the rest. Values of older versions (one watch) go to the registered H5600 watch once.
 * Keys are the plugin's own: the host keeps them under "ha." (the keys the app used before the plugin). Android-free.
 */
class HaStore(private val store: KeyValueStore) {
    /** The store itself, for the plugin's other switches (the raw read switch). */
    val kv: KeyValueStore get() = store

    // Whole numbers stay Long (not 1234.0) when a pending job is read back.
    private val gson = GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create()

    /** The per-watch values of Home Assistant; the legacy owner is the registered H5600 watch. */
    val watches = WatchStore(store, pickLegacyOwner = ::legacyOwnerCandidate)

    /**
     * The watch the values of a version without per-watch stores belong to: the registered watch of the H5600 family
     * (the one that had health data), else the only registered watch; null when neither is known.
     */
    fun legacyOwnerCandidate(): String? {
        val ids = registeredIds()
        return ids.firstOrNull { id -> modelInRegistration(id)?.contains("H5600", ignoreCase = true) == true } ?: ids.singleOrNull()
    }

    /** The model in the stored registration body of [deviceId]; null when not registered. */
    fun modelInRegistration(deviceId: String): String? = loadRegistration(deviceId)?.registration?.let { body ->
        runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject.get("model")?.asString }.getOrNull()
    }

    /** The model typed for [deviceId] on the Home Assistant screen; null = none typed. */
    fun confirmedModel(deviceId: String): String? = watches.get(deviceId, W_MODEL)?.value?.takeIf { it.isNotBlank() }

    fun saveModel(deviceId: String, model: String) {
        if (model.isBlank()) watches.delete(deviceId, W_MODEL) else watches.put(deviceId, W_MODEL, model.trim())
    }

    /** The model [deviceId] registers with: typed for this watch, else the one it registered with, else "" (from its name). */
    fun modelFor(deviceId: String): String = confirmedModel(deviceId) ?: modelInRegistration(deviceId).orEmpty()

    /** The saved setup, or null when URL or token is missing. */
    fun load(): HaConfig? = loadForm().takeIf { it.baseUrl.isNotBlank() && it.token.isNotBlank() }

    /** The saved setup even when incomplete, to fill the form. */
    fun loadForm(): HaConfig =
        runCatching { gson.fromJson(store.get(KEY_CONFIG), HaConfig::class.java) }.getOrNull() ?: HaConfig()

    fun save(config: HaConfig) = store.put(KEY_CONFIG, gson.toJson(config))

    fun loadRegistration(deviceId: String): HaRegistration? =
        runCatching { gson.fromJson(store.get(KEY_REG + deviceId), HaRegistration::class.java) }.getOrNull()
            ?.takeIf { it.webhookId.isNotBlank() }

    fun saveRegistration(deviceId: String, reg: HaRegistration) {
        store.put(KEY_REG + deviceId, gson.toJson(reg))
        val ids = registeredIds()
        if (deviceId !in ids) store.put(KEY_IDS, gson.toJson(ids + deviceId))
    }

    /**
     * The registration (webhook id, body, sensors hash) and the confirmed name of [from] go to [to]: a re-paired
     * watch has a new address but is the same device in HA. [from] is forgotten.
     */
    fun moveRegistration(from: String, to: String) {
        if (from == to) return
        store.get(KEY_REG + from)?.let { store.put(KEY_REG + to, it) }
        store.get(KEY_NAME + from)?.let { store.put(KEY_NAME + to, it) }
        store.put(KEY_IDS, gson.toJson(registeredIds().filter { it != from && it != to } + to))
        store.delete(KEY_REG + from)
        store.delete(KEY_NAME + from)
        watches.move(from, to)
    }

    /**
     * The name the owner confirmed for the watch [deviceId] before its first registration. It becomes the HA device
     * name, the sensor name prefix, the entity ids and the notify action, and is never changed after registering.
     */
    fun confirmedName(deviceId: String): String? = store.get(KEY_NAME + deviceId)?.takeIf { it.isNotBlank() }

    /** False for a blank name. A registered watch keeps its name: confirming again changes nothing in HA. */
    fun confirmName(deviceId: String, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || deviceId.isBlank()) return false
        store.put(KEY_NAME + deviceId, n)
        return true
    }

    fun registeredIds(): List<String> =
        runCatching { gson.fromJson(store.get(KEY_IDS), Array<String>::class.java)?.toList() }.getOrNull().orEmpty()

    /** Forgets everything: the setup, every registration and every watch's Home Assistant values. */
    fun forgetAll() {
        val ids = (registeredIds() + pendingIds()).toSet()
        ids.forEach { id ->
            store.delete(KEY_REG + id); store.delete(KEY_NAME + id)
            PER_WATCH.forEach { watches.delete(id, it) }
        }
        store.delete(KEY_IDS)
        store.delete(KEY_PENDING_IDS)
        store.delete(KEY_CONFIG)
        store.delete(KEY_ACTION)
        store.delete(KEY_ACTION_RESULT)
        LEGACY.forEach { store.delete(it) }
    }

    /** The `input_text.gshock_settings` value last applied to [deviceId]; null = never. */
    fun loadAppliedSettings(deviceId: String): String? = watches.get(deviceId, W_SETTINGS_APPLIED, KEY_SETTINGS_APPLIED)?.value

    fun saveAppliedSettings(deviceId: String, value: String) = watches.put(deviceId, W_SETTINGS_APPLIED, value)

    /** The newest job of its watch replaces a waiting one of the same watch; a waiting button event is kept until it is sent. */
    fun putPending(job: HaJob) {
        if (job.deviceId.isBlank()) return
        val old = getPending(job.deviceId)
        watches.put(job.deviceId, W_PENDING, gson.toJson(job.copy(event = job.event ?: old?.event)))
        val ids = pendingIds()
        if (job.deviceId !in ids) store.put(KEY_PENDING_IDS, gson.toJson(ids + job.deviceId))
    }

    fun getPending(deviceId: String): HaJob? =
        watches.get(deviceId, W_PENDING)?.value?.let { runCatching { gson.fromJson(it, HaJob::class.java) }.getOrNull() }

    /** The watches with a job waiting. A job an older version left (one for all) goes to its own watch first. */
    fun pendingIds(): List<String> {
        store.get(KEY_PENDING)?.let { raw ->
            store.delete(KEY_PENDING)
            runCatching { gson.fromJson(raw, HaJob::class.java) }.getOrNull()?.takeIf { it.deviceId.isNotBlank() }?.let { putPending(it) }
        }
        return runCatching { gson.fromJson(store.get(KEY_PENDING_IDS), Array<String>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    fun clearPending(deviceId: String) {
        watches.delete(deviceId, W_PENDING)
        store.put(KEY_PENDING_IDS, gson.toJson(pendingIds().filter { it != deviceId }))
    }

    fun saveResult(deviceId: String, text: String, ok: Boolean = true) {
        watches.put(deviceId, W_RESULT, text)
        watches.put(deviceId, W_RESULT_OK, if (ok) "yes" else "no")
    }

    /** False when the last send for [deviceId] failed; null before the first one. */
    fun lastResultOk(deviceId: String): Boolean? = watches.get(deviceId, W_RESULT_OK, KEY_RESULT_OK)?.value?.let { it == "yes" }

    fun lastResult(deviceId: String): String? = watches.get(deviceId, W_RESULT, KEY_RESULT)?.value

    /** The values of the last good send for [deviceId] (merged over earlier ones) and when it was. Never another watch's. */
    fun saveSent(deviceId: String, states: Map<String, Any>, atMillis: Long, attributes: Map<String, Map<String, Any?>> = emptyMap()) {
        if (deviceId.isBlank()) return
        val old = loadSent(deviceId)
        // HA replaces a sensor's attributes with each update: a sensor sent without attributes has none there now.
        val sentAttributes = attributes.filterKeys { it in states }.filterValues { it.isNotEmpty() }
        val keptAttributes = old.attributes.orEmpty().filterKeys { it !in states } + sentAttributes
        watches.put(deviceId, W_SENT, gson.toJson(HaSent(old.states + states, atMillis, keptAttributes)))
    }

    /** After a good send of [job]: its values and attributes, for a later register_sensor that has no fresh ones. */
    fun saveSent(job: HaJob, atMillis: Long) = saveSent(job.deviceId, job.states, atMillis, job.attributes.orEmpty())

    fun loadSent(deviceId: String): HaSent =
        watches.get(deviceId, W_SENT, KEY_SENT)?.value?.let { runCatching { gson.fromJson(it, HaSent::class.java) }.getOrNull() } ?: HaSent()

    /** The HA device page id of the watch [deviceId], once found. */
    fun saveDeviceId(deviceId: String, id: String) = watches.put(deviceId, W_DEVICE, id)

    fun loadDeviceId(deviceId: String): String? = watches.get(deviceId, W_DEVICE, KEY_DEVICE)?.value?.takeIf { it.isNotBlank() }

    /** Whether the watch [deviceId] showed notifications at its last connection; null = not known yet. */
    fun notificationsSupported(deviceId: String): Boolean? = watches.get(deviceId, W_PUSH_OK, KEY_PUSH_OK)?.value?.let { it == "yes" }

    fun saveNotificationsSupported(deviceId: String, ok: Boolean) = watches.put(deviceId, W_PUSH_OK, if (ok) "yes" else "no")

    /** The registered watch whose webhook is [webhookId]; null for none. */
    fun idOfWebhook(webhookId: String): String? = registeredIds().firstOrNull { loadRegistration(it)?.webhookId == webhookId }

    /** The name [deviceId] has in Home Assistant (its registration), else the confirmed one; null for neither. */
    fun nameInHa(deviceId: String): String? =
        loadRegistration(deviceId)?.registration?.let { body ->
            runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject.get("device_name")?.asString }.getOrNull()
        } ?: confirmedName(deviceId)

    /** The action a button press runs besides the event; null = nothing. */
    fun loadAction(): HaAction? =
        runCatching { gson.fromJson(store.get(KEY_ACTION), HaAction::class.java) }.getOrNull()?.takeIf { it.entityId.isNotBlank() }

    fun saveAction(action: HaAction?) = if (action == null) store.delete(KEY_ACTION) else store.put(KEY_ACTION, gson.toJson(action))

    /** The last press result in plain words, for example "Ran Goodnight at 22:14". */
    fun saveActionResult(text: String) = store.put(KEY_ACTION_RESULT, text)

    fun lastActionResult(): String? = store.get(KEY_ACTION_RESULT)

    /**
     * The groups of values that are sent: watch and settings always (the plugin maps everything it can), health while
     * [healthOn]. The health switch keeps the key of the old health group switch, so its state stays.
     */
    fun groupsOn(): Set<HaGroup> = HaGroup.entries.filter { store.get(KEY_GROUP + it.name) != "off" }.toSet() + if (healthOn()) setOf(HaGroup.HEALTH) else emptySet()

    /** "Share health data with Home Assistant": on until switched off. */
    fun healthOn(): Boolean = store.get(KEY_GROUP + HaGroup.HEALTH.name) != "off"

    fun setHealthOn(on: Boolean) = store.put(KEY_GROUP + HaGroup.HEALTH.name, if (on) "on" else "off")

    /** True while the health sensors of [deviceId] are disabled in Home Assistant (the switch was off at its last registration). */
    fun healthRemoved(deviceId: String): Boolean = store.get(KEY_HEALTH_REMOVED + deviceId) == "yes"

    fun setHealthRemoved(deviceId: String, removed: Boolean) =
        if (removed) store.put(KEY_HEALTH_REMOVED + deviceId, "yes") else store.delete(KEY_HEALTH_REMOVED + deviceId)

    private companion object {
        // Kept by versions before the per-watch stores: read once by the legacy owner, then deleted.
        const val KEY_PUSH_OK = "push.ok"
        const val KEY_SETTINGS_APPLIED = "settings.applied"
        const val KEY_PENDING = "pending"
        const val KEY_RESULT = "result"
        const val KEY_RESULT_OK = "result.ok"
        const val KEY_SENT = "sent"
        const val KEY_DEVICE = "device"
        val LEGACY = listOf(KEY_PUSH_OK, KEY_SETTINGS_APPLIED, KEY_PENDING, KEY_RESULT, KEY_RESULT_OK, KEY_SENT, KEY_DEVICE)

        // Per watch ([WatchStore] names).
        const val W_PUSH_OK = "push.ok"
        const val W_SETTINGS_APPLIED = "settings.applied"
        const val W_PENDING = "pending"
        const val W_RESULT = "result"
        const val W_RESULT_OK = "result.ok"
        const val W_SENT = "sent"
        const val W_DEVICE = "device"
        const val W_MODEL = "model"
        val PER_WATCH = listOf(W_PUSH_OK, W_SETTINGS_APPLIED, W_PENDING, W_RESULT, W_RESULT_OK, W_SENT, W_DEVICE, W_MODEL)

        const val KEY_GROUP = "group."
        const val KEY_HEALTH_REMOVED = "health_removed."
        const val KEY_CONFIG = "config"
        const val KEY_REG = "reg."
        const val KEY_NAME = "name."
        const val KEY_IDS = "regs"
        const val KEY_PENDING_IDS = "pending.ids"
        const val KEY_ACTION = "action"
        const val KEY_ACTION_RESULT = "action.result"
    }
}
