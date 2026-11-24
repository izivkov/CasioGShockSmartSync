package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.avmedia.smartsync.plugin.api.PluginStatus

/** A plugin's health for its Settings card: the last error and when, and the failures counted since the last success. */
data class PluginHealth(val lastError: String? = null, val atMillis: Long = 0, val failures: Int = 0)

/**
 * The plugins' health. Two kinds of failure: one the host caught (a throw, a time limit), cleared by the next callback
 * that works; one the plugin reported ([PluginStatus.Error]), cleared by its next [PluginStatus.Ok]. Android-free.
 */
class PluginHealthBoard(private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val caught: String? = null, val caughtAt: Long = 0, val reported: String? = null, val reportedAt: Long = 0, val failures: Int = 0)

    private val entries = HashMap<String, Entry>()
    private val _health = MutableStateFlow<Map<String, PluginHealth>>(emptyMap())
    val health: StateFlow<Map<String, PluginHealth>> = _health

    @Synchronized fun caught(id: String, text: String) = update(id) { it.copy(caught = text, caughtAt = clock(), failures = it.failures + 1) }

    @Synchronized fun callbackOk(id: String) = update(id) { if (it.caught == null) it else it.copy(caught = null, failures = if (it.reported == null) 0 else it.failures) }

    @Synchronized fun reported(id: String, status: PluginStatus) = update(id) {
        when (status) {
            is PluginStatus.Error -> it.copy(reported = status.text.take(120), reportedAt = clock(), failures = it.failures + 1)
            is PluginStatus.Ok -> it.copy(reported = null, failures = if (it.caught == null) 0 else it.failures)
        }
    }

    @Synchronized fun clear(id: String) = update(id) { Entry() }

    fun of(id: String): PluginHealth = _health.value[id] ?: PluginHealth()

    private fun update(id: String, change: (Entry) -> Entry) {
        val e = change(entries[id] ?: Entry())
        entries[id] = e
        val latest = listOfNotNull(e.caught?.let { it to e.caughtAt }, e.reported?.let { it to e.reportedAt }).maxByOrNull { it.second }
        _health.value = _health.value + (id to PluginHealth(latest?.first, latest?.second ?: 0, e.failures))
    }

    companion object {
        /** "Last error: <text>, 14:05 (3 failures)"; null when there is none. */
        fun line(h: PluginHealth, time: (Long) -> String): String? = h.lastError?.let {
            "Last error: $it, ${time(h.atMillis)}" + if (h.failures > 1) " (${h.failures} failures)" else ""
        }
    }
}
