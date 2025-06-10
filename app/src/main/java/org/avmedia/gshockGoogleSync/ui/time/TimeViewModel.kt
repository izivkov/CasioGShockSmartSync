package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject

data class TimeState(
    val timer: Int = 0,
    val homeTime: String = "",
    val batteryLevel: Int = 0,
    val temperature: Int = 0,
    val watchName: String = ""
)

sealed interface TimeAction {
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : TimeAction
    data class UpdateTimer(val timeMs: Int) : TimeAction
    data object SendTimeToWatch : TimeAction
    data object RefreshState : TimeAction
}

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val api: GShockRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimeState())
    val state = _state.asStateFlow()

    init {
        refreshState()
    }

    fun onAction(action: TimeAction) {
        when (action) {
            is TimeAction.SetTimer -> {
                _state.value = _state.value.copy(
                    timer = action.hours * 3600 + action.minutes * 60 + action.seconds
                )
            }

            is TimeAction.UpdateTimer -> {
                viewModelScope.launch {
                    api.setTimer(action.timeMs)
                    AppSnackbar(appContext.getString(R.string.timer_set))
                }
            }

            TimeAction.SendTimeToWatch -> {
                viewModelScope.launch {
                    runCatching {
                        val timeOffset = LocalDataStorage.getFineTimeAdjustment(appContext)
                        val timeMs = System.currentTimeMillis() + timeOffset
                        AppSnackbar(appContext.getString(R.string.sending_time_to_watch))
                        api.setTime(timeMs.toString())  // Convert Long to String
                        AppSnackbar(appContext.getString(R.string.time_set))
                        refreshState()
                    }.onFailure { e ->
                        ProgressEvents.onNext("ApiError", e.message ?: "")
                    }
                }
            }

            TimeAction.RefreshState -> refreshState()
        }
    }

    private fun refreshState() {
        viewModelScope.launch {
            runCatching {
                _state.value = TimeState(
                    timer = api.getTimer(),
                    homeTime = api.getHomeTime(),
                    batteryLevel = api.getBatteryLevel(),
                    temperature = api.getWatchTemperature(),
                    watchName = api.getWatchName()
                )
            }.onFailure {
                ProgressEvents.onNext("ApiError")
            }
        }
    }
}
