package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Home Assistant" screen: URL and token are checked and saved once; each watch registers itself on its first connection. */
@HiltViewModel
class HaViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val store = WebhookStorage.haStore(context)
    private val discovery = HaDiscovery(context)

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<HaUi> = _ui

    init {
        if (store.load() != null) { refreshActions(); lookUpDevice() }
    }

    /** Rebuilds everything that comes from the store; keeps what only lives here (check, search, loading). */
    private fun snapshot(old: HaUi? = null): HaUi {
        val sent = store.loadSent()
        val id = watchId()
        val reg = id?.let { store.loadRegistration(it) }
        val form = store.loadForm()
        val failed = store.lastResultOk() == false
        val firmware = (sent.states["watch_firmware"] as? String)?.takeIf { it != "unknown" }
        return HaUi(
            form = form,
            check = old?.check ?: HaCheckUi.Idle,
            find = old?.find ?: HaFindUi.Idle,
            watch = HaWatchUi(
                registered = reg != null,
                firmware = firmware,
                lastUpdate = HaSentView.time(sent.at),
                failed = failed,
                lastText = store.lastResult().orEmpty(),
                deviceUrl = HaDeviceLookup.deviceUrl(form.baseUrl, store.loadDeviceId()),
                lookingUpDevice = old?.watch?.lookingUpDevice ?: false,
            ),
            sent = HaSentUi(HaSentView.groups(sent, store.groupsOn()), sending = store.getPending() != null, failed = failed),
            actions = (old?.actions ?: HaActionsUi()).copy(chosen = store.loadAction(), lastRun = store.lastActionResult().orEmpty()),
        )
    }

    private fun watchId(): String? {
        val address = runCatching { org.avmedia.gshockapi.WatchInfo.getAddress() }.getOrDefault("")
        return if (address.isNotBlank()) HaPayloads.deviceId(address) else store.registeredIds().firstOrNull()
    }

    private fun update(change: (HaUi) -> HaUi) = _ui.update { change(it) }

    /** Reloads the automations, scripts and scenes from HA. */
    fun refreshActions() {
        update { it.copy(actions = it.actions.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { HaActionRunner(HaOkHttpTransport, store).fetch() }
            update { it.copy(actions = it.actions.copy(items = list.items, loading = false, error = list.error)) }
        }
    }

    /** Null = "Nothing (only the gshock_button event)". */
    fun choose(action: HaAction?) {
        store.saveAction(action)
        update { it.copy(actions = it.actions.copy(chosen = action)) }
    }

    fun setGroup(group: HaGroup, on: Boolean) {
        store.setGroupOn(group, on)
        update { snapshot(it) }
    }

    fun save(baseUrl: String, token: String, deviceName: String, model: String) {
        update { it.copy(check = HaCheckUi.Checking) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { HaClient(HaOkHttpTransport, store).saveSettings(baseUrl, token, deviceName, model) }
            val check = if (result is HaResult.Ok) HaCheckUi.Ok(result.message) else HaCheckUi.Error(result.message, result.detail)
            update { snapshot(it.copy(check = check)) }
            if (result is HaResult.Ok) { refreshActions(); lookUpDevice() }
        }
    }

    /** Finds the device page id of the registered watch, once. Quiet when it fails: the button then opens the device list. */
    private fun lookUpDevice() {
        val cfg = store.load() ?: return
        val reg = watchId()?.let { store.loadRegistration(it) } ?: return
        if (store.loadDeviceId() != null) return
        val name = runCatching { JsonParser.parseString(reg.registration).asJsonObject.get("device_name").asString }.getOrNull() ?: return
        update { it.copy(watch = it.watch.copy(lookingUpDevice = true)) }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { HaDeviceLookup.find(HaOkHttpTransport, cfg, name) }
            id?.let { store.saveDeviceId(it) }
            update { snapshot(it.copy(watch = it.watch.copy(lookingUpDevice = false))) }
        }
    }

    fun findOnNetwork() {
        val found = ArrayList<HaFound>()
        val started = discovery.start(
            onFound = { f -> synchronized(found) { if (found.none { it.url == f.url }) found += f; update { it.copy(find = HaFindUi.Searching(found.toList())) } } },
            onFailed = { update { it.copy(find = HaFindUi.Unavailable) } },
        )
        if (!started) { update { it.copy(find = HaFindUi.Unavailable) }; return }
        update { it.copy(find = HaFindUi.Searching(emptyList())) }
        viewModelScope.launch {
            delay(SEARCH_MS)
            discovery.stop()
            update { if (it.find is HaFindUi.Searching) it.copy(find = HaFindUi.Done(it.find.found)) else it }
        }
    }

    fun forget() {
        store.forgetAll()
        update { HaUi(form = store.loadForm()) }
    }

    override fun onCleared() = discovery.stop()

    private companion object { const val SEARCH_MS = 8_000L }
}
