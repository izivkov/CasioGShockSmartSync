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

    @Volatile
    private var overwriteConfirmed = false

    fun loadHooks(): List<Webhook> {
        val raw = store.get(KEY_HOOKS)
        if (raw.isNullOrBlank()) {
            unreadable = false
            return emptyList()
        }
        val parsed = WebhookJson.parseHooks(raw)
        if (parsed == null) {
            markUnreadable(raw)
            return emptyList()
        }
        unreadable = false
        return parsed
    }

    /** False (and nothing written) while the stored list is unreadable and the user has not confirmed. */
    fun saveHooks(hooks: List<Webhook>): Boolean {
        if (!overwriteConfirmed) {
            val raw = store.get(KEY_HOOKS)
            if (!raw.isNullOrBlank() && WebhookJson.parseHooks(raw) == null) {
                markUnreadable(raw)
                return false
            }
        }
        store.put(KEY_HOOKS, WebhookJson.hooksToJson(hooks))
        overwriteConfirmed = false
        unreadable = false
        return true
    }

    /** The user accepted losing the unreadable list (a raw copy was kept). */
    fun confirmOverwrite() {
        overwriteConfirmed = true
        unreadable = false
    }

    /** Keeps a raw copy once per distinct unreadable content. */
    private fun markUnreadable(raw: String) {
        unreadable = true
        val mark = raw.hashCode().toString()
        if (store.get(KEY_UNREADABLE_MARK) != mark) {
            store.put(KEY_UNREADABLE_PREFIX + now(), raw)
            store.put(KEY_UNREADABLE_MARK, mark)
        }
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
