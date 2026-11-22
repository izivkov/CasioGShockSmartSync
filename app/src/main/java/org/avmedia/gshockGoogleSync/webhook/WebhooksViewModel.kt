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

    /** The Test button: idle, sending, the result in words, or a failure to run. */
    private val _testState = MutableStateFlow<TestUi>(TestUi.Idle)
    val testState: StateFlow<TestUi> = _testState

    /** Values for the live preview: the ones the phone and app know now (watch values need a connection). */
    private val _previewValues = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val previewValues: StateFlow<Map<String, Any?>> = _previewValues

    fun loadPreviewValues() {
        viewModelScope.launch {
            runCatching { dispatcher.sampleValues() }.onSuccess { _previewValues.value = it }
        }
    }

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

    fun clearTestReport() {
        _testState.value = TestUi.Idle
    }

    /** Sends [hook] now (with the values currently in the editor, saved or not). */
    fun test(hook: Webhook) {
        saveDraft(hook) // the draft, never the list
        _testState.value = TestUi.Sending
        viewModelScope.launch {
            val report = runCatching { dispatcher.test(hook) }
            _testState.value = report.fold(
                onSuccess = { TestUi.Done(readable(it)) },
                onFailure = {
                    WebhookDiagnostics.logError("Test failed: ${WebhookDiagnostics.describe(it)}", it)
                    TestUi.Failed(WebhookDiagnostics.describe(it))
                },
            )
        }
    }

    internal fun readable(r: WebhookDispatcher.TestReport): ReadableTest = TestResultText.of(
        r.outcome.status, r.outcome.message, r.rendered.unknown, r.rendered.failed, r.rendered.missing, r.watchConnected,
    )
}
