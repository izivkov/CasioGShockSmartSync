package org.avmedia.gShockSmartSyncCompose.ui.others

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.applicationContext
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.utils.LocalDataStorage
import org.avmedia.gShockSmartSyncCompose.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection

class PreConnectionViewModel : ViewModel() {
    private val noWatchString = applicationContext().getString(R.string.no_watch)
    private val initialValue =
        LocalDataStorage.get(applicationContext(), "LastDeviceName", noWatchString) as String

    private val _watchName = MutableStateFlow(initialValue)
    val watchName: StateFlow<String> = _watchName

    init {
        createSubscription()
    }

    // Method to subscribe to ProgressEvents and handle the "DeviceName" action
    private fun createSubscription() {
        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = (ProgressEvents.getPayload("DeviceName") as? String)
                    ?.takeIf { it.isNotBlank() } ?: noWatchString
                _watchName.value = deviceName
            }
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    fun forget() {
        viewModelScope.launch {
            LocalDataStorage.deleteAsync(applicationContext(), "LastDeviceAddress")
            LocalDataStorage.deleteAsync(applicationContext(), "LastDeviceName")
            Connection.breakWait()
            ProgressEvents.onNext("DeviceName", "")
            ProgressEvents.onNext("WaitForConnection")
        }
    }
}

