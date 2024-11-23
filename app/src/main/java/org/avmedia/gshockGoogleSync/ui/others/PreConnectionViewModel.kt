package org.avmedia.gshockGoogleSync.ui.others

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import javax.inject.Inject

@HiltViewModel
class PreConnectionViewModel @Inject constructor(
    private val api: GShockRepository,
    @ApplicationContext private val appContext: Context // Inject application context
) : ViewModel() {

    private val noWatchString = appContext.getString(R.string.no_watch)
    private val initialValue =
        LocalDataStorage.get(appContext, "LastDeviceName", noWatchString) as String

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
            LocalDataStorage.deleteAsync(appContext, "LastDeviceAddress")
            LocalDataStorage.deleteAsync(appContext, "LastDeviceName")
            Connection.breakWait()
            ProgressEvents.onNext("DeviceName", "")
            ProgressEvents.onNext("WaitForConnection")
        }
    }
}

