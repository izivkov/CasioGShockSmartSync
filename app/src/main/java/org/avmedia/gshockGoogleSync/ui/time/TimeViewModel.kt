package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.voice.VoiceCommandManager
import org.avmedia.gshockGoogleSync.voice.VoiceDispatcher
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockapi.WatchInfo
import javax.inject.Inject
import kotlin.random.Random

enum class StepDataOption {
    TODAY, HOURLY, DAILY
}

data class TimeState(
    val timer: Int = 0,
    val homeTime: String = "",
    val batteryLevel: Int = 0,
    val temperature: Int = 0,
    val watchName: String = "",
    val timeZoneOption: TimeSettingsStorage.TimeZoneOption = TimeSettingsStorage.TimeZoneOption.SYSTEM,
    val timeOffset: Long = 0L,
    val stepCounterData: StepCounterData = StepCounterData.unavailable(),
    val selectedStepDataOption: StepDataOption = StepDataOption.TODAY,
    val isListening: Boolean = false,
    val isVoiceCommandSupported: Boolean = false
)

sealed interface TimeAction {
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : TimeAction
    data class UpdateTimer(val timeMs: Int) : TimeAction
    data object SendTimeToWatch : TimeAction
    data object RefreshState : TimeAction
    data class SetTimeZoneOption(val option: TimeSettingsStorage.TimeZoneOption) : TimeAction
    data class SetStepDataOption(val option: StepDataOption) : TimeAction
    data object StartVoiceCommand : TimeAction
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val api: GShockRepository,
    private val timeSettingsStorage: TimeSettingsStorage,
    private val watchTimeUpdater: WatchTimeUpdater,
    private val watchFeatureManager: IWatchFeatureManager,
    private val voiceCommandManager: VoiceCommandManager,
    private val voiceDispatcher: VoiceDispatcher,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimeState())
    val state = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private var saveJob: Job? = null
    private var stepPollJob: Job? = null

    init {
        refreshState()

        if (WatchInfo.hasStepCounter || WatchInfo.hasStepCounterMock) {
            startStepCounterPolling()
        }
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
                        AppSnackbar(appContext.getString(R.string.sending_time_to_watch))
                        watchTimeUpdater.updateTime()
                        AppSnackbar(appContext.getString(R.string.time_set))
                        refreshState()
                    }.onFailure { e ->
                        AppSnackbar(e.message ?: "Api Error")
                    }
                }
            }

            TimeAction.RefreshState -> refreshState()

            is TimeAction.SetStepDataOption -> {
                _state.value = _state.value.copy(
                    selectedStepDataOption = action.option
                )
            }

            is TimeAction.SetTimeZoneOption -> {
                val offset = calculateOffset(action.option)
                _state.value = _state.value.copy(
                    timeZoneOption = action.option,
                    timeOffset = offset
                )
                timeSettingsStorage.setTimeZoneOption(action.option)

                saveJob?.cancel()
                saveJob = viewModelScope.launch {
                    delay(0)
                    timeSettingsStorage.save()
                    saveJob = null
                }
            }

            TimeAction.StartVoiceCommand -> {
                if (voiceCommandManager.isRecognitionAvailable()) {
                    _state.value = _state.value.copy(isListening = true)
                    voiceCommandManager.startListening(
                        onResult = { text ->
                            _state.value = _state.value.copy(isListening = false)
                            voiceDispatcher.dispatch(text)
                        },
                        onError = { error ->
                            _state.value = _state.value.copy(isListening = false)
                            AppSnackbar(error)
                        }
                    )
                } else {
                    AppSnackbar(appContext.getString(R.string.voice_recognition_unavailable))
                }
            }
        }
    }

    private fun startStepCounterPolling() {
        stepPollJob?.cancel()
        stepPollJob = viewModelScope.launch {
            while (isActive) {
                delay(3000) // 3 seconds interval
                if (watchFeatureManager.isFeatureSupported("time.step_counter")) {
                    runCatching {
                        val stepData = if (WatchInfo.hasStepCounterMock) {
                            generateMockStepData()
                        } else {
                            api.getStepCount()
                        }
                        _state.value = _state.value.copy(stepCounterData = stepData)
                    }
                }
            }
        }
    }

    private fun calculateOffset(option: TimeSettingsStorage.TimeZoneOption): Long {
        return SolarTimeHelper.calculateTimeOffset(appContext, option)
    }

    override fun onCleared() {
        super.onCleared()
        stepPollJob?.cancel()
        saveJob?.let {
            saveJob?.cancel()
            viewModelScope.launch {
                timeSettingsStorage.save()
            }
        }
    }

    private fun refreshState() {
        viewModelScope.launch {
            runCatching {
                timeSettingsStorage.load()
                val option = timeSettingsStorage.getTimeZoneOption()
                val offset = calculateOffset(option)

                _state.value = TimeState(
                    timer = api.getTimer(),
                    homeTime = if (watchFeatureManager.isFeatureSupported("time.home_time")) api.getHomeTime() else "",
                    batteryLevel = api.getBatteryLevel(),
                    temperature = api.getWatchTemperature(),
                    watchName = api.getWatchName(),
                    timeZoneOption = option,
                    timeOffset = offset,
                    stepCounterData = if (watchFeatureManager.isFeatureSupported("time.step_counter")) {
                        if (WatchInfo.hasStepCounterMock) {
                            generateMockStepData()
                        } else {
                            api.getStepCount()
                        }
                    } else StepCounterData.unavailable(),
                    isVoiceCommandSupported = voiceCommandManager.isRecognitionAvailable()
                )
            }.onFailure {
                AppSnackbar("Api Error")
            }
        }
    }

    private fun generateMockStepData(): StepCounterData {
        val hourly = List(144) { index ->
            val hour = index / 6
            if (hour in 7..22) {
                Random.nextInt(20, 350)
            } else {
                0
            }
        }

        val daily = List(14) {
            Random.nextInt(4000, 12500)
        }

        return StepCounterData(
            dayOfWeek = 6,
            month = 8,
            dayOfMonth = 15,
            hourlySteps = hourly,
            dailyHistory = daily,
            currentDaySteps = Random.nextInt(8000, 9500) // Slight variation to show real-time changes if mocked
        )
    }
}
