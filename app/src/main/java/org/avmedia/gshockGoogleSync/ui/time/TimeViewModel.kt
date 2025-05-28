package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val api: GShockRepository,
    @ApplicationContext private val appContext: Context // Inject application context
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
            AppSnackbar(appContext.getString(R.string.timer_set))
        }
    }

    fun sendTimeToWatch() {
        viewModelScope.launch {
            runCatching {
                val timeOffset = LocalDataStorage.getFineTimeAdjustment(appContext)
                val timeMs = System.currentTimeMillis() + timeOffset
                AppSnackbar(appContext.getString(R.string.sending_time_to_watch))
                api.setTime(timeMs = timeMs)
                AppSnackbar(appContext.getString(R.string.time_set))

                // Refresh the Home Time on the screen in case changed by setting time.
                _homeTime.value = api.getHomeTime()
            }.onFailure { e ->
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }

        }
    }

    init {
        viewModelScope.launch {
            runCatching {
                _timer.value = api.getTimer()
                _homeTime.value = api.getHomeTime()
                _batteryLevel.value = api.getBatteryLevel()
                _temperature.value = api.getWatchTemperature()
                _watchName.value = api.getWatchName()
            }.onFailure {
                ProgressEvents.onNext("ApiError")
            }

        }
    }
}