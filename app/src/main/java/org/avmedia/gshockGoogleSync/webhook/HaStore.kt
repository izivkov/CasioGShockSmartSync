package org.avmedia.gshockGoogleSync.webhook

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

    fun registeredIds(): List<String> =
        runCatching { gson.fromJson(store.get(KEY_IDS), Array<String>::class.java)?.toList() }.getOrNull().orEmpty()

    /** Forgets everything: the setup and every registration. The devices stay in HA until deleted there. */
    fun forgetAll() {
        registeredIds().forEach { store.delete(KEY_REG + it) }
        store.delete(KEY_IDS)
        store.delete(KEY_CONFIG)
        store.delete(KEY_PENDING)
    }

    /** The newest job replaces a waiting one; a waiting button event is kept until it is sent. */
    fun putPending(job: HaJob) {
        val old = getPending()
        store.put(KEY_PENDING, gson.toJson(job.copy(event = job.event ?: old?.event)))
    }

    fun getPending(): HaJob? = runCatching { gson.fromJson(store.get(KEY_PENDING), HaJob::class.java) }.getOrNull()

    fun clearPending() = store.delete(KEY_PENDING)

    fun saveResult(text: String) = store.put(KEY_RESULT, text)

    fun lastResult(): String? = store.get(KEY_RESULT)

    private companion object {
        const val KEY_CONFIG = "ha.config"
        const val KEY_REG = "ha.reg."
        const val KEY_IDS = "ha.regs"
        const val KEY_PENDING = "ha.pending"
        const val KEY_RESULT = "ha.result"
    }
}
