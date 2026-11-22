package org.avmedia.gshockGoogleSync.webhook

import androidx.annotation.Keep
import com.google.gson.Gson

interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)
}

/** The open editor, kept so it survives leaving the screen or the app being killed. */
@Keep
data class WebhookDraft(val hook: Webhook = Webhook(), val isNew: Boolean = false)

/**
 * The webhook list and the editor draft on top of a [KeyValueStore]. Android-free so it can be
 * unit tested. Rule: a failed load never erases saved data.
 */
class WebhookRepository(
    private val store: KeyValueStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val KEY_HOOKS = "webhooks.list"
        const val KEY_DRAFT = "webhooks.draft"
        const val KEY_UNREADABLE_PREFIX = "webhooks.list.unreadable."
        const val KEY_UNREADABLE_MARK = "webhooks.list.unreadable.mark"
    }

    private val gson = Gson()

    /** True while the stored list is there but could not be read. */
    @Volatile
    var unreadable: Boolean = false
        private set

    fun loadHooks(): List<Webhook> =
        WebhookJson.hooksFromJson(store.get(KEY_HOOKS))

    fun saveHooks(hooks: List<Webhook>): Boolean {
        store.put(KEY_HOOKS, WebhookJson.hooksToJson(hooks))
        return true
    }

    /** The user accepted losing the unreadable list. */
    fun confirmOverwrite() {
        unreadable = false
    }

    fun saveDraft(draft: WebhookDraft) = store.put(KEY_DRAFT, gson.toJson(draft))

    fun loadDraft(): WebhookDraft? {
        val raw = store.get(KEY_DRAFT)
        if (raw.isNullOrBlank()) return null
        return try {
            gson.fromJson(raw, WebhookDraft::class.java)?.takeIf { it.hook.id.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun clearDraft() = store.delete(KEY_DRAFT)
}
