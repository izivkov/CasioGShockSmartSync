package org.avmedia.gshockGoogleSync.homeassistant

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

/** Sensor values last sent to HA and the time (epoch millis, 0 = never). */
@Keep
data class HaSent(val states: Map<String, Any> = emptyMap(), val at: Long = 0)

/** The Home Assistant setup, one registration per watch and the newest unsent job, on a [KeyValueStore]. Android-free. */
class HaStore(private val store: KeyValueStore) {
    // Whole numbers stay Long (not 1234.0) when a pending job is read back.
    private val gson = GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create()

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

    /** Forgets everything: the setup and every registration. The devices stay in HA until deleted there. */
    fun forgetAll() {
        registeredIds().forEach { store.delete(KEY_REG + it); store.delete(KEY_NAME + it) }
        store.delete(KEY_IDS)
        store.delete(KEY_CONFIG)
        store.delete(KEY_PENDING)
        store.delete(KEY_ACTION)
        store.delete(KEY_ACTION_RESULT)
        store.delete(KEY_RESULT_OK)
        store.delete(KEY_SENT)
        store.delete(KEY_DEVICE)
        store.delete(KEY_PUSH_OK)
        store.delete(KEY_SETTINGS_APPLIED)
    }

    /** The `input_text.gshock_settings` value last applied to the watch; null = never. */
    fun loadAppliedSettings(): String? = store.get(KEY_SETTINGS_APPLIED)

    fun saveAppliedSettings(value: String) = store.put(KEY_SETTINGS_APPLIED, value)

    /** The newest job replaces a waiting one; a waiting button event is kept until it is sent. */
    fun putPending(job: HaJob) {
        val old = getPending()
        store.put(KEY_PENDING, gson.toJson(job.copy(event = job.event ?: old?.event)))
    }

    fun getPending(): HaJob? = runCatching { gson.fromJson(store.get(KEY_PENDING), HaJob::class.java) }.getOrNull()

    fun clearPending() = store.delete(KEY_PENDING)

    fun saveResult(text: String, ok: Boolean = true) {
        store.put(KEY_RESULT, text)
        store.put(KEY_RESULT_OK, if (ok) "yes" else "no")
    }

    /** False when the last send failed; null before the first one. */
    fun lastResultOk(): Boolean? = store.get(KEY_RESULT_OK)?.let { it == "yes" }

    /** The values of the last good send (merged over earlier ones) and when it was, for the screen. */
    fun saveSent(states: Map<String, Any>, atMillis: Long) {
        val merged = loadSent().states + states
        store.put(KEY_SENT, gson.toJson(HaSent(merged, atMillis)))
    }

    fun loadSent(): HaSent =
        runCatching { gson.fromJson(store.get(KEY_SENT), HaSent::class.java) }.getOrNull() ?: HaSent()

    /** The HA device page id of the watch, once found. */
    fun saveDeviceId(id: String) = store.put(KEY_DEVICE, id)

    fun loadDeviceId(): String? = store.get(KEY_DEVICE)?.takeIf { it.isNotBlank() }

    fun lastResult(): String? = store.get(KEY_RESULT)

    /** Whether the connected watch showed notifications at its last connection; null = not known yet. */
    fun notificationsSupported(): Boolean? = store.get(KEY_PUSH_OK)?.let { it == "yes" }

    fun saveNotificationsSupported(ok: Boolean) = store.put(KEY_PUSH_OK, if (ok) "yes" else "no")

    /** The action a button press runs besides the event; null = nothing. */
    fun loadAction(): HaAction? =
        runCatching { gson.fromJson(store.get(KEY_ACTION), HaAction::class.java) }.getOrNull()?.takeIf { it.entityId.isNotBlank() }

    fun saveAction(action: HaAction?) = if (action == null) store.delete(KEY_ACTION) else store.put(KEY_ACTION, gson.toJson(action))

    /** The last press result in plain words, for example "Ran Goodnight at 22:14". */
    fun saveActionResult(text: String) = store.put(KEY_ACTION_RESULT, text)

    fun lastActionResult(): String? = store.get(KEY_ACTION_RESULT)

    /** The groups of values that are sent; all on until switched off. */
    fun groupsOn(): Set<HaGroup> = HaGroup.entries.filter { store.get(KEY_GROUP + it.name) != "off" }.toSet()

    fun setGroupOn(group: HaGroup, on: Boolean) = store.put(KEY_GROUP + group.name, if (on) "on" else "off")

    private companion object {
        const val KEY_PUSH_OK = "ha.push.ok"
        const val KEY_SETTINGS_APPLIED = "ha.settings.applied"
        const val KEY_GROUP = "ha.group."
        const val KEY_CONFIG = "ha.config"
        const val KEY_REG = "ha.reg."
        const val KEY_NAME = "ha.name."
        const val KEY_IDS = "ha.regs"
        const val KEY_PENDING = "ha.pending"
        const val KEY_RESULT = "ha.result"
        const val KEY_RESULT_OK = "ha.result.ok"
        const val KEY_SENT = "ha.sent"
        const val KEY_DEVICE = "ha.device"
        const val KEY_ACTION = "ha.action"
        const val KEY_ACTION_RESULT = "ha.action.result"
    }
}
