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
        _status.value = "Forgotten. The devices stay in Home Assistant until you delete them there."
    }
}
