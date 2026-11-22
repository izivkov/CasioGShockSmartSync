package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WebhooksViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcher: WebhookDispatcher,
    val probe: WatchProbe,
) : ViewModel() {

    private val _hooks = MutableStateFlow(WebhookStorage.loadHooks(context))
    val hooks: StateFlow<List<Webhook>> = _hooks

    private val repository = WebhookStorage.repository(context)

    /** True when the stored webhooks could not be read; they are kept untouched until the user confirms. */
    private val _unreadable = MutableStateFlow(repository.unreadable)
    val unreadable: StateFlow<Boolean> = _unreadable

    fun confirmOverwrite() {
        repository.confirmOverwrite()
        _unreadable.value = false
    }

    fun loadDraft(): WebhookDraft? = repository.loadDraft()
    fun saveDraft(hook: Webhook) =
        repository.saveDraft(WebhookDraft(hook, isNew = _hooks.value.none { it.id == hook.id }))
    fun clearDraft() = repository.clearDraft()

    private val _results = MutableStateFlow(WebhookStorage.loadResults(context))
    val results: StateFlow<Map<String, WebhookResult>> = _results

    /** Text of the last Test run, null while none ran or one is running. */
    private val _testReport = MutableStateFlow<String?>(null)
    val testReport: StateFlow<String?> = _testReport

    val variables: List<Variable> = dispatcher.registry.variables

    fun refreshResults() {
        _results.value = WebhookStorage.loadResults(context)
    }

    /** False when nothing was written because the stored list is unreadable. */
    fun save(hook: Webhook): Boolean {
        val list = _hooks.value
        val updated =
            if (list.any { it.id == hook.id }) list.map { if (it.id == hook.id) hook else it }
            else list + hook
        if (!WebhookStorage.saveHooks(context, updated)) {
            _unreadable.value = true
            return false
        }
        _hooks.value = updated
        return true
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _hooks.value.firstOrNull { it.id == id }?.let { save(it.copy(enabled = enabled)) }
    }

    fun delete(id: String) {
        WebhookStorage.removeHook(context, id)
        _hooks.value = _hooks.value.filterNot { it.id == id }
        refreshResults()
    }

    /** Posts the probe result to the enabled webhooks; returns how many were queued. */
    fun sendProbeToWebhooks(): Int = dispatcher.sendProbe(probe.result().toJson())

    /** Posts the newest app log lines (event "debug_log"); returns how many webhooks it was queued for. */
    fun sendLogToWebhooks(): Int = dispatcher.sendProbe(
        DebugLog.toJson(
            org.avmedia.gshockGoogleSync.session.WatchModelOverride.displayName(org.avmedia.gshockapi.WatchInfo.name),
            java.time.OffsetDateTime.now().withNano(0).toString(),
        )
    )

    fun clearTestReport() {
        _testReport.value = null
    }

    /** Sends [hook] now (with the values currently in the editor, saved or not). */
    fun test(hook: Webhook) {
        saveDraft(hook) // the draft, never the list
        _testReport.value = "Sending..."
        viewModelScope.launch {
            val report = runCatching { dispatcher.test(hook) }
            _testReport.value = report.fold(
                onSuccess = { formatReport(it) },
                onFailure = {
                    WebhookDiagnostics.logError("Test failed: ${WebhookDiagnostics.describe(it)}", it)
                    "Test failed:\n" + WebhookDiagnostics.describe(it)
                },
            )
        }
    }

    internal fun formatReport(r: WebhookDispatcher.TestReport): String = buildString {
        val status = r.outcome.status
        appendLine(if (status != null) "HTTP $status" else "No response")
        if (r.outcome.message.isNotEmpty()) appendLine(r.outcome.message)
        if (r.rendered.unknown.isNotEmpty()) appendLine("Unknown variables: ${r.rendered.unknown.joinToString()}")
        if (r.rendered.failed.isNotEmpty()) appendLine("Variables that failed: ${r.rendered.failed.joinToString()}")
        if (r.rendered.missing.isNotEmpty()) appendLine("No value for: ${r.rendered.missing.joinToString()}")
        if (!r.watchConnected) appendLine("Watch not connected: watch values are empty.")
    }.trimEnd()
}
