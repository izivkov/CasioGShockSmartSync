package org.avmedia.gshockGoogleSync.ui.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.MainActivity.Companion.applicationContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class TimeViewModel @Inject constructor(
    @Named("api") private val api: GShockRepository
) : ViewModel() {
    private val _timer = MutableStateFlow(0)
    val timer = _timer
    fun setTimer(hours: Int, minutes: Int, seconds: Int) {
        _timer.value = hours * 3600 + minutes * 60 + seconds
    }

    private val _homeTime = MutableStateFlow("")
    val homeTime = _homeTime

    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel = _batteryLevel

    private val _temperature = MutableStateFlow(0)
    val temperature = _temperature

    private val _watchName = MutableStateFlow("")
    val watchName = _watchName

    fun sendTimerToWatch(timeMs: Int) {
        viewModelScope.launch {
            api.setTimer(timeMs)
            AppSnackbar("Timer Set")
        }
    }

    fun sendTimeToWatch() {
        viewModelScope.launch {
            try {
                val timeOffset = LocalDataStorage.getFineTimeAdjustment(applicationContext())
                val timeMs = System.currentTimeMillis() + timeOffset
                AppSnackbar("Sending time to watch...")
                api.setTime(timeMs = timeMs)
                api.setTime(timeMs = timeMs)

                AppSnackbar("Time Set")

                // Refresh the Home Time on the screen in case changed by setting time.
                _homeTime.value = api.getHomeTime()

            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }
        }
    }

    init {
        viewModelScope.launch {
            try {
                _timer.value = api.getTimer()
                _homeTime.value = api.getHomeTime()
                _batteryLevel.value = api.getBatteryLevel()
                _temperature.value = api.getWatchTemperature()
                _watchName.value = api.getWatchName()
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError")
            }
        }
    }
}