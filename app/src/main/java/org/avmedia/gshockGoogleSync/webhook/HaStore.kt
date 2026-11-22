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
)

/** The Home Assistant link: where it is, how to get in, and the webhook the registration gave us. */
@Keep
data class HaConfig(
    val baseUrl: String = "",
    val token: String = "",
    val webhookId: String = "",
    /** The registration body, kept so a deleted registration (HTTP 410) can be made again. */
    val registration: String = "",
    /** [HaSensors.fingerprint] of the sensors HA knows. Empty = register them before the next update. */
    val sensorsHash: String = "",
    /** What the owner typed in the form; empty = the default from the watch name. */
    val deviceName: String = "",
    val model: String = "",
)

/** The Home Assistant link and the newest unsent job, on top of a [KeyValueStore]. Android-free. */
class HaStore(private val store: KeyValueStore) {
    // Whole numbers stay Long (not 1234.0) when a pending job is read back.
    private val gson = GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create()

    fun load(): HaConfig? = runCatching { gson.fromJson(store.get(KEY_CONFIG), HaConfig::class.java) }
        .getOrNull()?.takeIf { it.webhookId.isNotBlank() }

    /** The saved base URL and token even while not connected, to fill the form. */
    fun loadForm(): HaConfig =
        runCatching { gson.fromJson(store.get(KEY_CONFIG), HaConfig::class.java) }.getOrNull() ?: HaConfig()

    fun save(config: HaConfig) = store.put(KEY_CONFIG, gson.toJson(config))

    /** Forgets the webhook id only; the form keeps URL and token. */
    fun disconnect() {
        save(loadForm().copy(webhookId = "", registration = "", sensorsHash = ""))
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
        const val KEY_PENDING = "ha.pending"
        const val KEY_RESULT = "ha.result"
    }
}
