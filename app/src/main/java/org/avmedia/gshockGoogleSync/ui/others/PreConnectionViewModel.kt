package org.avmedia.gshockGoogleSync.ui.others

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.CrashReportHelper
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ICDPDelegate
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

@HiltViewModel
class PreConnectionViewModel
@Inject
constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    // Constants for internal state keys and strings
    private companion object {
        const val KEY_LAST_DEVICE_NAME = "LastDeviceName"
        const val KEY_LAST_DEVICE_ADDRESS = "LastDeviceAddress"
        const val EVENT_DEVICE_NAME = "DeviceName"
        const val EVENT_DEVICE_ADDRESS = "DeviceAddress"
        const val EVENT_NO_PAIRED = "NoPairedDevices"
        const val PREFIX_CASIO = "CASIO"
        const val METHOD_REMOVE_BOND = "removeBond"
    }

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
            val savedName = LocalDataStorage.get(appContext, KEY_LAST_DEVICE_NAME, noWatchString) ?: noWatchString
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

    fun associateWithUi(context: Context, delegate: ICDPDelegate) {
        try {
            if (context is android.app.Activity && context.isFinishing) {
                delegate.onError(appContext.getString(R.string.pairing_activity_finishing))
                return
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                showPreparingUi()
            }

            associate(context, object : ICDPDelegate {
                override fun onChooserReady(chooserLauncher: IntentSender) {
                    hidePreparingUi()
                    delegate.onChooserReady(chooserLauncher)
                }

                override fun onError(error: String) {
                    hidePreparingUi()
                    delegate.onError(error)
                }
            })
        } catch (e: Exception) {
            hidePreparingUi()
            delegate.onError(appContext.getString(R.string.pairing_failed_generic, e.message ?: "Unknown error"))
        }
    }

    fun loadPairedDevices() {
        val associations = api.getAssociationsWithNames(appContext)
        val lastAddress = LocalDataStorage.get(appContext, KEY_LAST_DEVICE_ADDRESS, "")

        val items = associations
            .distinctBy { it.address.lowercase() }
            .map { association ->
                val isLastUsed = association.address.equals(lastAddress, ignoreCase = true)
                val name = association.name

                if (!name.isNullOrBlank()) {
                    DeviceItem(name, association.address, isLastUsed)
                } else {
                    val storedName = LocalDataStorage.getDeviceName(appContext, association.address)
                    DeviceItem(storedName ?: noWatchString, association.address, isLastUsed)
                }
            }

        _pairedDevices.value = formatDeviceNames(items)
    }

    private fun formatDeviceNames(items: List<DeviceItem>): List<DeviceItem> {
        val nameCount: MutableMap<String, Int> = mutableMapOf()
        val result: MutableList<DeviceItem> = mutableListOf()

        items.forEach { item ->
            val baseName = item.name.removePrefix(PREFIX_CASIO).trim()
            nameCount[baseName] = nameCount.getOrDefault(baseName, 0) + 1
        }

        val currentCount: MutableMap<String, Int> = mutableMapOf()
        items.forEach { item ->
            val baseName = item.name.removePrefix(PREFIX_CASIO).trim()
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

    private fun createSubscription() {
        val eventActions = arrayOf(
            EventAction(EVENT_DEVICE_NAME) {
                val deviceName = (ProgressEvents.getPayload(EVENT_DEVICE_NAME) as? String)
                    ?.takeIf { it.isNotBlank() } ?: noWatchString
                _watchName.value = deviceName
            },
            EventAction(EVENT_DEVICE_ADDRESS) {
                val address = ProgressEvents.getPayload(EVENT_DEVICE_ADDRESS) as? String
                if (!address.isNullOrBlank()) {
                    viewModelScope.launch {
                        LocalDataStorage.put(appContext, KEY_LAST_DEVICE_ADDRESS, address)
                        loadPairedDevices()
                    }
                }
            },
            EventAction(EVENT_NO_PAIRED) {
                // Handle no devices if necessary
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
            try {
                // Using dict/mapOf for structured logging
                val logDetails: Map<String, String> = mapOf(
                    "address" to address,
                    "name" to name,
                    "action" to "setting_device"
                )
                Timber.i("Updating local storage: $logDetails")

                LocalDataStorage.put(appContext, KEY_LAST_DEVICE_ADDRESS, address)
                LocalDataStorage.addDeviceAddress(appContext, address)
                LocalDataStorage.put(appContext, KEY_LAST_DEVICE_NAME, name)
                LocalDataStorage.setDeviceName(appContext, address, name)

                _watchName.value = name
                api.startObservingDevicePresence(appContext, address)
                loadPairedDevices()

                CrashReportHelper.clearPairingCrashFlag(appContext)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set device")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disassociate(context: Context, address: String) {
        viewModelScope.launch {
            try {
                // 1. Remove software association via API (clears CDM association)
                api.disassociate(context, address)
                LocalDataStorage.removeDeviceAddress(context, address)

                // 2. Handle hardware bond safely (no reflection, version-aware)
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice? = adapter?.getRemoteDevice(address)
                device?.let { btDevice ->
                    if (btDevice.bondState != BluetoothDevice.BOND_NONE) {
                        Timber.i("Device $address bonded (state: ${btDevice.bondState}). CDM disassociate initiated unbond process.")
                        // CDM disassociate often triggers bond removal automatically via broadcast
                        // Monitor existing ACTION_BOND_STATE_CHANGED receiver for completion
                    }
                }

                // 3. Clear last device if matching
                val lastAddress = LocalDataStorage.get(context, KEY_LAST_DEVICE_ADDRESS, "")
                if (address.equals(lastAddress, ignoreCase = true)) {
                    LocalDataStorage.put(context, KEY_LAST_DEVICE_ADDRESS, "")
                    LocalDataStorage.put(context, KEY_LAST_DEVICE_NAME, noWatchString)
                    _watchName.value = noWatchString
                }

                // Refresh UI and log
                loadPairedDevices()
                Timber.i("Device $address removed from app. Bond cleanup via CDM (user may need to forget in Settings if persists).")

            } catch (e: Exception) {
                Timber.e(e, "Failed to disassociate device: $address")
            }
        }
    }

    fun onPairingTriggered() {
        _triggerPairing.value = false
    }
}
