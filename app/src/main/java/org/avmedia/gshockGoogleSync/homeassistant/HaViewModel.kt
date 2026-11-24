package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watch.AppStores
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
    private val store = AppStores.haStore(context)
    private val discovery = HaDiscovery(context)
    private val rawSwitch = AppStores.rawSwitch(context)
    private val dashboards = HaDashboardService(HaOkHttpWs, store, HaOkHttpTransport)

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<HaUi> = _ui

    init {
        if (store.load() != null) { refreshActions(); lookUpDevice(); refreshDashboard() }
    }

    /** The watch id and its name in Home Assistant, once it is registered. */
    private fun watchNames(): Pair<String, String>? {
        val id = watchId() ?: return null
        val body = store.loadRegistration(id)?.registration ?: return null
        val name = runCatching { JsonParser.parseString(body).asJsonObject.get("device_name").asString }.getOrNull() ?: return null
        return id to name
    }

    /** Looks at the dashboard address quietly: "Create" or "Update" on the button. A failure leaves "Create". */
    private fun refreshDashboard() {
        val (id, name) = watchNames() ?: return
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { dashboards.status(id, name) }
            update { it.copy(dash = it.dash.copy(status = status)) }
        }
    }

    fun createDashboard() {
        val (id, name) = watchNames() ?: return
        update { it.copy(dash = it.dash.copy(working = true, message = "", failed = false, url = "")) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { dashboards.createOrUpdate(id, name) }
            val status = if (r.ok) withContext(Dispatchers.IO) { dashboards.status(id, name) } else null
            update { it.copy(dash = it.dash.copy(status = status ?: it.dash.status, working = false, message = r.message, failed = !r.ok, url = r.url)) }
        }
    }

    /** Puts the dashboard YAML on the clipboard. */
    fun copyYaml(context: Context) {
        val (id, name) = watchNames() ?: return
        update { it.copy(dash = it.dash.copy(working = true, message = "", failed = false, url = "")) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { dashboards.yaml(id, name) }
            if (r.ok) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Home Assistant dashboard", r.text))
            }
            update { it.copy(dash = it.dash.copy(working = false, message = r.message, failed = !r.ok)) }
        }
    }

    /** Rebuilds everything that comes from the store; keeps what only lives here (check, search, loading). */
    private fun snapshot(old: HaUi? = null): HaUi {
        // Everything shown is the connected (else the last) watch's own; the others are only listed by name.
        val id = watchId()
        val key = id.orEmpty()
        val sent = store.loadSent(key)
        val reg = id?.let { store.loadRegistration(it) }
        val form = store.loadForm()
        val failed = store.lastResultOk(key) == false
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
                lastText = store.lastResult(key).orEmpty(),
                deviceUrl = HaDeviceLookup.deviceUrl(form.baseUrl, store.loadDeviceId(key)),
                lookingUpDevice = old?.watch?.lookingUpDevice ?: false,
                notificationsSupported = store.notificationsSupported(key) != false,
                rawUntil = rawSwitch.onUntilText(),
                suggestedName = connectedId()?.let { HaPayloads.defaultModel(org.avmedia.gshockapi.WatchInfo.name) }.orEmpty(),
                confirmedName = id?.let { store.confirmedName(it) },
                name = if (reg != null) store.nameInHa(key).orEmpty() else "",
                model = store.modelFor(key),
                others = store.registeredIds().filter { it != key }.mapNotNull { store.nameInHa(it) },
            ),
            sent = HaSentUi(HaSentView.groups(sent, store.groupsOn()), sending = store.getPending(key) != null, failed = failed),
            actions = (old?.actions ?: HaActionsUi()).copy(chosen = store.loadAction(), lastRun = store.lastActionResult().orEmpty()),
            dash = old?.dash ?: HaDashUi(),
        )
    }

    /** The connected watch's id, null when no watch is connected. */
    private fun connectedId(): String? {
        val address = runCatching { org.avmedia.gshockapi.WatchInfo.getAddress() }.getOrDefault("")
        return if (address.isNotBlank()) HaPayloads.deviceId(address) else null
    }

    /** The owner confirmed the name before the first registration: saved, then the watch registers with it at once. */
    fun confirmName(name: String, model: String) {
        val id = connectedId() ?: return
        if (store.loadRegistration(id) != null) return
        if (!store.confirmName(id, name)) return
        // The model belongs to this watch only: another watch registers with its own.
        store.saveModel(id, model)
        update { snapshot(it) }
        HaPushRouter.updateSensorsHook?.invoke()
    }

    /** The connected watch, else the last connected one when it is registered, else the first registered. */
    private fun watchId(): String? = connectedId()
        ?: org.avmedia.gshockGoogleSync.watch.CurrentWatch.keyOrLast(context).takeIf { it.isNotBlank() && store.loadRegistration(it) != null }
        ?: store.registeredIds().firstOrNull()

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

    fun setRawSwitch(on: Boolean) {
        if (on) rawSwitch.turnOn() else rawSwitch.turnOff()
        update { snapshot(it) }
    }

    fun setGroup(group: HaGroup, on: Boolean) {
        store.setGroupOn(group, on)
        update { snapshot(it) }
    }

    fun save(baseUrl: String, token: String, deviceName: String, model: String) {
        update { it.copy(check = HaCheckUi.Checking) }
        val id = watchId()
        viewModelScope.launch {
            val form = store.loadForm()
            // URL and token are the setup's; the model typed here is this watch's ([HaStore.saveModel]).
            val result = withContext(Dispatchers.IO) { HaClient(HaOkHttpTransport, store).saveSettings(baseUrl, token, form.deviceName.ifBlank { deviceName }, form.model) }
            val changedModel = id != null && model.trim() != store.modelFor(id)
            if (result is HaResult.Ok && id != null && changedModel) store.saveModel(id, model)
            val check = if (result is HaResult.Ok) HaCheckUi.Ok(result.message) else HaCheckUi.Error(result.message, result.detail)
            update { snapshot(it.copy(check = check)) }
            if (result is HaResult.Ok) { refreshActions(); lookUpDevice() }
            if (result is HaResult.Ok && changedModel && store.loadRegistration(id.orEmpty()) != null) HaPushRouter.updateSensorsHook?.invoke()
        }
    }

    /** Finds the device page id of the registered watch, once. Quiet when it fails: the button then opens the device list. */
    private fun lookUpDevice() {
        val cfg = store.load() ?: return
        val watch = watchId() ?: return
        val reg = store.loadRegistration(watch) ?: return
        if (store.loadDeviceId(watch) != null) return
        val name = runCatching { JsonParser.parseString(reg.registration).asJsonObject.get("device_name").asString }.getOrNull() ?: return
        update { it.copy(watch = it.watch.copy(lookingUpDevice = true)) }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { HaDeviceLookup.find(HaOkHttpTransport, cfg, name) }
            id?.let { store.saveDeviceId(watch, it) }
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
