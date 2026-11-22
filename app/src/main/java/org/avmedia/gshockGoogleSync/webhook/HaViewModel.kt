package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import android.os.Build
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
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.session.WatchModelOverride
import org.avmedia.gshockapi.WatchInfo

/** The "Home Assistant" screen: URL, token, Connect and Disconnect. The token is never logged. */
@HiltViewModel
class HaViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val store = WebhookStorage.haStore(context)

    private val _form = MutableStateFlow(store.loadForm())
    val form: StateFlow<HaConfig> = _form

    private val _status = MutableStateFlow(store.lastResult().orEmpty())
    val status: StateFlow<String> = _status

    val connected: Boolean get() = store.load() != null

    fun connect(baseUrl: String, token: String, typedName: String, typedModel: String) {
        val address = WatchInfo.getAddress()
        if (address.isBlank()) {
            _status.value = "Connect to the watch once first: the device is named after it."
            return
        }
        val name = WatchModelOverride.displayName(WatchInfo.name)
        val (deviceName, model) = HaPayloads.resolveNames(name, typedName, typedModel)
        val body = HaPayloads.registration(
            deviceId = HaPayloads.deviceId(address),
            deviceName = deviceName,
            model = model,
            appVersion = BuildConfig.VERSION_NAME,
            osVersion = Build.VERSION.RELEASE,
        )
        _status.value = "Connecting..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { HaClient(HaOkHttpTransport, store).connect(baseUrl, token, body, typedName.trim(), typedModel.trim()) }
            _status.value = if (result is HaResult.Ok) result.message else "Failed: ${result.message}"
            _form.value = store.loadForm()
        }
    }

    fun disconnect() {
        store.disconnect()
        _form.value = store.loadForm()
        _status.value = "Disconnected. The device stays in Home Assistant until you delete it there."
    }
}
