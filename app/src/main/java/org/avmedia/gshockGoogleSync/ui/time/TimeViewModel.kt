package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
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

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimeState())
    val state = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

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
                    _uiEvents.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.timer_set)))
                }
            }

            TimeAction.SendTimeToWatch -> {
                viewModelScope.launch {
                    runCatching {
                        val timeOffset = LocalDataStorage.getFineTimeAdjustment(appContext)
                        val timeMs = System.currentTimeMillis() + timeOffset
                        _uiEvents.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.sending_time_to_watch)))
                        api.setTime(timeMs = timeMs)
                        _uiEvents.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.time_set)))
                        refreshState()
                    }.onFailure { e ->
                        _uiEvents.emit(UiEvent.ShowSnackbar(e.message ?: "Api Error"))
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
                    homeTime = if (WatchInfo.hasHomeTime) api.getHomeTime() else "",
                    batteryLevel = api.getBatteryLevel(),
                    temperature = api.getWatchTemperature(),
                    watchName = api.getWatchName()
                )
            }.onFailure {
                _uiEvents.emit(UiEvent.ShowSnackbar("Api Error"))
            }
        }
    }
}
