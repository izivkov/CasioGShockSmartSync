package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Home Assistant" screen: URL, token, name and model are saved once; each watch registers itself on its first connection. */
@HiltViewModel
class HaViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val store = WebhookStorage.haStore(context)

    private val _form = MutableStateFlow(store.loadForm())
    val form: StateFlow<HaConfig> = _form

    private val _status = MutableStateFlow(store.lastResult().orEmpty())
    val status: StateFlow<String> = _status

    private val _actions = MutableStateFlow<List<HaAction>>(emptyList())
    val actions: StateFlow<List<HaAction>> = _actions

    private val _chosen = MutableStateFlow(store.loadAction())
    val chosen: StateFlow<HaAction?> = _chosen

    private val _actionStatus = MutableStateFlow(store.lastActionResult().orEmpty())
    val actionStatus: StateFlow<String> = _actionStatus

    init { if (store.load() != null) refreshActions() }

    /** Reloads the automations, scripts and scenes from HA. */
    fun refreshActions() {
        _actionStatus.value = "Loading..."
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { HaActionRunner(HaOkHttpTransport, store).fetch() }
            _actions.value = list.items
            _actionStatus.value = list.error ?: store.lastActionResult().orEmpty()
        }
    }

    /** Null = "Nothing (only the gshock_button event)". */
    fun choose(action: HaAction?) {
        store.saveAction(action)
        _chosen.value = action
    }

    val saved: Boolean get() = store.load() != null

    fun save(baseUrl: String, token: String, deviceName: String, model: String) {
        _status.value = "Checking..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { HaClient(HaOkHttpTransport, store).saveSettings(baseUrl, token, deviceName, model) }
            _status.value = if (result is HaResult.Ok) result.message else "Failed: ${result.message}"
            _form.value = store.loadForm()
        }
    }

    fun forget() {
        store.forgetAll()
        _form.value = store.loadForm()
        _chosen.value = null
        _actions.value = emptyList()
        _actionStatus.value = ""
        _status.value = "Forgotten. The devices stay in Home Assistant until you delete them there."
    }
}
