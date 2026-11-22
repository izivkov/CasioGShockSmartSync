package org.avmedia.gshockGoogleSync.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookRepositoryTest {
    private class MemoryStore : KeyValueStore {
        val map = LinkedHashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun delete(key: String) { map.remove(key) }
    }

    private val store = MemoryStore()
    private var clock = 1_700_000_000_000L
    private val repo = WebhookRepository(store) { clock }
    private val hook = Webhook(id = "h1", name = "A", url = "https://example.com/x")

    @Test
    fun corruptStoredListIsNeverOverwrittenBySaving() {
        store.map[WebhookRepository.KEY_HOOKS] = "{this is not json"
        assertEquals(emptyList<Webhook>(), repo.loadHooks())
        assertTrue(repo.unreadable)

        val saved = repo.saveHooks(listOf(hook))

        assertFalse("save must be refused while the stored list is unreadable", saved)
        assertEquals("{this is not json", store.map[WebhookRepository.KEY_HOOKS])
        val copies = store.map.filterKeys { it.startsWith(WebhookRepository.KEY_UNREADABLE_PREFIX) && !it.endsWith(".mark") }
        assertEquals(listOf("{this is not json"), copies.values.toList())
        assertTrue(copies.keys.single().endsWith(clock.toString()))
    }

    @Test
    fun savingWithoutALoadFirstIsAlsoProtected() {
        store.map[WebhookRepository.KEY_HOOKS] = "[{broken"
        assertFalse(repo.saveHooks(listOf(hook)))
        assertEquals("[{broken", store.map[WebhookRepository.KEY_HOOKS])
    }

    @Test
    fun theRawCopyIsMadeOnlyOnce() {
        store.map[WebhookRepository.KEY_HOOKS] = "garbage"
        repo.loadHooks()
        clock += 5_000
        repo.loadHooks()
        repo.saveHooks(listOf(hook))
        val copies = store.map.keys.filter { it.startsWith(WebhookRepository.KEY_UNREADABLE_PREFIX) && !it.endsWith(".mark") }
        assertEquals(1, copies.size)
    }

    @Test
    fun afterConfirmingTheListCanBeOverwritten() {
        store.map[WebhookRepository.KEY_HOOKS] = "garbage"
        repo.loadHooks()
        repo.confirmOverwrite()
        assertTrue(repo.saveHooks(listOf(hook)))
        assertEquals(listOf(hook), repo.loadHooks())
        assertFalse(repo.unreadable)
    }

    @Test
    fun aGoodListAndAnEmptyStoreSaveNormally() {
        assertTrue(repo.saveHooks(listOf(hook)))
        assertEquals(listOf(hook), repo.loadHooks())
        assertFalse(repo.unreadable)
        val fresh = WebhookRepository(MemoryStore())
        assertTrue(fresh.saveHooks(emptyList()))
    }

    @Test
    fun draftIsPersistedRestoredAndCleared() {
        assertNull(repo.loadDraft())
        val draft = WebhookDraft(hook.copy(name = "typed but not saved"), isNew = true)
        repo.saveDraft(draft)
        assertEquals(draft, WebhookRepository(store).loadDraft())
        repo.clearDraft()
        assertNull(repo.loadDraft())
    }

    @Test
    fun savingTheDraftNeverTouchesTheList() {
        repo.saveHooks(listOf(hook))
        repo.saveDraft(WebhookDraft(hook.copy(name = "changed"), isNew = false))
        assertEquals(listOf(hook), repo.loadHooks())
    }

    @Test
    fun aCorruptDraftIsIgnored() {
        store.map[WebhookRepository.KEY_DRAFT] = "{nope"
        assertNull(repo.loadDraft())
    }
}
