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
) : ViewModel() {

    private val _hooks = MutableStateFlow(WebhookStorage.loadHooks(context))
    val hooks: StateFlow<List<Webhook>> = _hooks

    private val _results = MutableStateFlow(WebhookStorage.loadResults(context))
    val results: StateFlow<Map<String, WebhookResult>> = _results

    /** Text of the last Test run, null while none ran or one is running. */
    private val _testReport = MutableStateFlow<String?>(null)
    val testReport: StateFlow<String?> = _testReport

    val variables: List<Variable> = dispatcher.registry.variables

    fun refreshResults() {
        _results.value = WebhookStorage.loadResults(context)
    }

    fun save(hook: Webhook) {
        val list = _hooks.value
        _hooks.value =
            if (list.any { it.id == hook.id }) list.map { if (it.id == hook.id) hook else it }
            else list + hook
        WebhookStorage.saveHooks(context, _hooks.value)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _hooks.value.firstOrNull { it.id == id }?.let { save(it.copy(enabled = enabled)) }
    }

    fun delete(id: String) {
        WebhookStorage.removeHook(context, id)
        _hooks.value = _hooks.value.filterNot { it.id == id }
        refreshResults()
    }

    fun clearTestReport() {
        _testReport.value = null
    }

    /** Sends [hook] now (with the values currently in the editor, saved or not). */
    fun test(hook: Webhook) {
        _testReport.value = "Sending..."
        viewModelScope.launch {
            val report = runCatching { dispatcher.test(hook) }
            _testReport.value = report.fold(
                onSuccess = { formatReport(it) },
                onFailure = { "Test failed: ${it.javaClass.simpleName}" },
            )
        }
    }

    internal fun formatReport(r: WebhookDispatcher.TestReport): String = buildString {
        val status = r.outcome.status
        appendLine(if (status != null) "HTTP $status" else "No response")
        if (r.outcome.message.isNotEmpty()) appendLine(r.outcome.message)
        if (r.rendered.unknown.isNotEmpty()) appendLine("Unknown variables: ${r.rendered.unknown.joinToString()}")
        if (r.rendered.missing.isNotEmpty()) appendLine("No value for: ${r.rendered.missing.joinToString()}")
        if (!r.watchConnected) appendLine("Watch not connected: watch values are empty.")
    }.trimEnd()
}
