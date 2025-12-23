package org.avmedia.gshockGoogleSync.ui.others

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ICDPDelegate
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject

@HiltViewModel
class PreConnectionViewModel @Inject constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val appContext: Context // Inject application context
) : ViewModel() {

    private val noWatchString = appContext.getString(R.string.no_watch)
    private val _watchName = MutableStateFlow(noWatchString)
    val watchName: StateFlow<String> = _watchName

    private val _triggerPairing = MutableStateFlow(false)
    val triggerPairing: StateFlow<Boolean> = _triggerPairing

    data class DeviceItem(val name: String, val address: String, val isLastUsed: Boolean)

    private val _pairedDevices = MutableStateFlow<List<DeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<DeviceItem>> = _pairedDevices

    private val _showPreparing = MutableStateFlow(false)
    val showPreparing: StateFlow<Boolean> = _showPreparing

    init {
        viewModelScope.launch {
            val savedName =
                LocalDataStorage.get(appContext, "LastDeviceName", noWatchString) ?: noWatchString
            _watchName.value = savedName
            loadPairedDevices()
        }
        createSubscription()
    }

    fun hidePreparing() {
        _showPreparing.value = false
    }

    private fun showPreparingUi() {
        _showPreparing.value = true
    }

    private fun hidePreparingUi() {
        _showPreparing.value = false
    }

    fun associateWithUi(
        context: Context,
        delegate: ICDPDelegate
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            showPreparingUi()
        }

        associate(
            context,
            object : ICDPDelegate {

                override fun onChooserReady(chooserLauncher: IntentSender) {
                    hidePreparingUi()
                    delegate.onChooserReady(chooserLauncher)
                }

                override fun onError(error: String) {
                    hidePreparingUi()
                    delegate.onError(error)
                }
            }
        )
    }

    private fun loadPairedDevices() {
        val associations = api.getAssociationsWithNames(appContext)
        val lastAddress = LocalDataStorage.get(appContext, "LastDeviceAddress", "")

        val items = associations
            .distinctBy { it.address.lowercase() } // Also handle uniqueness case-insensitively
            .map { association ->
                // Use equals with ignoreCase = true
                val isLastUsed = association.address.equals(lastAddress, ignoreCase = true)
                val name = association.name

                if (name != null && name.isNotBlank()) {
                    DeviceItem(name, association.address, isLastUsed)
                } else {
                    val storedName = LocalDataStorage.getDeviceName(appContext, association.address)
                    DeviceItem(
                        storedName ?: noWatchString,
                        association.address,
                        isLastUsed
                    )
                }
            }

        _pairedDevices.value = formatDeviceNames(items)
    }

    private fun formatDeviceNames(items: List<DeviceItem>): List<DeviceItem> {
        val nameCount = mutableMapOf<String, Int>()
        val result = mutableListOf<DeviceItem>()

        // First pass: count occurrences
        items.forEach { item ->
            val baseName = item.name.removePrefix("CASIO").trim()
            nameCount[baseName] = nameCount.getOrDefault(baseName, 0) + 1
        }

        // Second pass: apply suffixes if needed
        val currentCount = mutableMapOf<String, Int>()
        items.forEach { item ->
            val baseName = item.name.removePrefix("CASIO").trim()
            val total = nameCount[baseName] ?: 1
            val formattedName = if (total > 1) {
                val index = currentCount.getOrDefault(baseName, 0) + 1
                currentCount[baseName] = index
                "$baseName-$index"
            } else {
                baseName
            }
            result.add(item.copy(name = formattedName))
        }
        return result
    }

    // Method to subscribe to ProgressEvents and handle the "DeviceName" action
    private fun createSubscription() {
        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = (ProgressEvents.getPayload("DeviceName") as? String)
                    ?.takeIf { it.isNotBlank() } ?: noWatchString
                _watchName.value = deviceName
            },
            EventAction("DeviceAddress") {
                val address = ProgressEvents.getPayload("DeviceAddress") as? String
                if (!address.isNullOrBlank()) {
                    viewModelScope.launch {
                        LocalDataStorage.put(appContext, "LastDeviceAddress", address)
                        loadPairedDevices()
                    }
                }
            },
            EventAction("NoPairedDevices") {
                // _triggerPairing.value = true
            }
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    fun associate(context: Context, delegate: ICDPDelegate) {
        api.associate(context, delegate)
    }

    @SuppressLint("NewApi")
    fun setDevice(address: String, name: String) {
        viewModelScope.launch {
            LocalDataStorage.put(appContext, "LastDeviceAddress", address)
            LocalDataStorage.addDeviceAddress(appContext, address)
            LocalDataStorage.put(appContext, "LastDeviceName", name)
            LocalDataStorage.setDeviceName(appContext, address, name)
            _watchName.value = name
            (appContext as? org.avmedia.gshockGoogleSync.GShockApplication)?.startObservingDevicePresence(appContext, address)
            loadPairedDevices()
        }
    }

    fun selectDevice(device: DeviceItem) {
        viewModelScope.launch {
            LocalDataStorage.put(appContext, "LastDeviceAddress", device.address)
            LocalDataStorage.put(appContext, "LastDeviceName", device.name)
            loadPairedDevices()
        }
    }

    fun onPairingTriggered() {
        _triggerPairing.value = false
    }

    fun disassociate(context: Context, address: String) {
        viewModelScope.launch {
            api.disassociate(context, address)
            LocalDataStorage.removeDeviceAddress(context, address)

            val lastAddress = LocalDataStorage.get(context, "LastDeviceAddress", "")
            if (address == lastAddress) {
                LocalDataStorage.put(context, "LastDeviceAddress", "")
                LocalDataStorage.put(context, "LastDeviceName", noWatchString)
                _watchName.value = noWatchString
            }
            loadPairedDevices()
        }
    }
}

