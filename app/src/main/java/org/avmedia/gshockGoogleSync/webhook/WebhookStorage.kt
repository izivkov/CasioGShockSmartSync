package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/**
 * Webhooks and their last results, kept in the app's [LocalDataStorage]. Plain object so the
 * background worker can use it without dependency injection.
 */
object WebhookStorage {
    private const val KEY_RESULTS = "webhooks.results"
    private const val KEY_PENDING = "webhooks.pending."
    private const val KEY_LAST_ATTEMPT = "webhooks.lastAttempt."
    const val KEY_LAST_SYNC = "webhooks.lastSync"

    private val lock = Any()

    internal class LocalKeyValueStore(private val context: Context) : KeyValueStore {
        override fun get(key: String): String? = LocalDataStorage.get(context, key)
        // Wait for each write: the fire-and-forget put/delete can land out of order (see StorageOrderTest).
        override fun put(key: String, value: String) = kotlinx.coroutines.runBlocking { LocalDataStorage.putAndWait(context, key, value) }
        override fun delete(key: String) = kotlinx.coroutines.runBlocking { LocalDataStorage.deleteAsync(context, key) }
    }

    @Volatile
    private var repository: WebhookRepository? = null

    fun repository(context: Context): WebhookRepository =
        repository ?: synchronized(lock) {
            repository ?: WebhookRepository(LocalKeyValueStore(context.applicationContext)).also { repository = it }
        }

    /** The Home Assistant link, on the same storage. */
    fun haStore(context: Context): HaStore = HaStore(LocalKeyValueStore(context.applicationContext))

    fun toggles(context: Context): ServiceToggles = ServiceToggles(LocalKeyValueStore(context.applicationContext))

    fun loadHooks(context: Context): List<Webhook> = repository(context).loadHooks()

    /** False when the stored list is unreadable and was not overwritten. */
    fun saveHooks(context: Context, hooks: List<Webhook>): Boolean = repository(context).saveHooks(hooks)

    fun loadResults(context: Context): Map<String, WebhookResult> =
        WebhookJson.resultsFromJson(LocalDataStorage.get(context, KEY_RESULTS))

    fun saveResult(context: Context, hookId: String, result: WebhookResult) = synchronized(lock) {
        val all = loadResults(context).toMutableMap()
        all[hookId] = result
        LocalDataStorage.put(context, KEY_RESULTS, WebhookJson.resultsToJson(all))
    }

    fun removeHook(context: Context, hookId: String) = synchronized(lock) {
        saveHooks(context, loadHooks(context).filterNot { it.id == hookId })
        val all = loadResults(context).toMutableMap()
        if (all.remove(hookId) != null) {
            LocalDataStorage.put(context, KEY_RESULTS, WebhookJson.resultsToJson(all))
        }
        LocalDataStorage.delete(context, KEY_PENDING + hookId)
        LocalDataStorage.delete(context, KEY_LAST_ATTEMPT + hookId)
    }

    /** The rendered body waiting to be sent for [hookId] (the newest one replaces older ones). */
    fun putPending(context: Context, hookId: String, body: String) =
        LocalDataStorage.put(context, KEY_PENDING + hookId, body)

    fun getPending(context: Context, hookId: String): String? =
        LocalDataStorage.get(context, KEY_PENDING + hookId)

    fun clearPending(context: Context, hookId: String) =
        LocalDataStorage.delete(context, KEY_PENDING + hookId)

    fun lastAttempt(context: Context, hookId: String): Long? =
        LocalDataStorage.get(context, KEY_LAST_ATTEMPT + hookId)?.toLongOrNull()

    fun markAttempt(context: Context, hookId: String, millis: Long) =
        LocalDataStorage.put(context, KEY_LAST_ATTEMPT + hookId, millis.toString())
}
