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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.ScratchpadSteps
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.voice.VoiceCommandManager
import org.avmedia.gshockGoogleSync.voice.VoiceDispatcher
import org.avmedia.gshockGoogleSync.voice.VoiceCommand
import org.avmedia.gshockGoogleSync.voice.VoiceSpeechFeedback
import org.avmedia.gshockGoogleSync.utils.subscribeToProgressEvents
import org.avmedia.gshockapi.ProgressEvents
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
    val stepGoal: Int = 10000,
    val weight: Int = 700, // 100g units
    val calories: Float = 0f,
    val distanceKm: Float = 0f,
    val isListening: Boolean = false,
    val isVoiceCommandSupported: Boolean = false,
    val isVoiceVerbose: Boolean = true
)

sealed interface TimeAction {
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : TimeAction
    data class UpdateTimer(val timeMs: Int) : TimeAction
    data object SendTimeToWatch : TimeAction
    data object RefreshState : TimeAction
    data class SetTimeZoneOption(val option: TimeSettingsStorage.TimeZoneOption) : TimeAction
    data class SetStepDataOption(val option: StepDataOption) : TimeAction
    data class SetStepGoal(val goal: Int) : TimeAction
    data class SetWeight(val weight: Int) : TimeAction
    data object ClearStepHistory : TimeAction
    data class SetVoiceVerbose(val enabled: Boolean) : TimeAction
    data object StartVoiceCommand : TimeAction
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val api: GShockRepository,
    private val timeSettingsStorage: TimeSettingsStorage,
    private val scratchpadSteps: ScratchpadSteps,
    private val watchTimeUpdater: WatchTimeUpdater,
    private val watchFeatureManager: IWatchFeatureManager,
    private val voiceCommandManager: VoiceCommandManager,
    private val voiceDispatcher: VoiceDispatcher,
    private val voiceSpeechFeedback: VoiceSpeechFeedback,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimeState())
    val state = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private var eventSubscriptionName: String? = null
    private var saveJob: Job? = null
    private var stepPollJob: Job? = null

    init {
        refreshState()
        setupEventSubscription()

        if (WatchInfo.hasStepCounter || WatchInfo.hasStepCounterMock) {
            startStepCounterPolling()
        }
    }

    private fun setupEventSubscription() {
        // ProgressEvents silently drops a second subscription registered under a
        // name it has already seen - which "TimeViewModel" was, the moment this
        // ViewModel is ever recreated. A unique name per instance guarantees
        // this instance's subscription actually registers - without this, the
        // retry logic below never even runs, because the event never arrives.
        eventSubscriptionName = subscribeToProgressEvents("TimeViewModel", arrayOf(
            org.avmedia.gshockapi.EventAction("TimerUpdated") {
                viewModelScope.launch {
                    refreshTimerAfterExternalWrite()
                }
            }
        ))
    }

    private suspend fun refreshTimerAfterExternalWrite(
        maxAttempts: Int = 3,
        retryDelayMs: Long = 500
    ) {
        val before = _state.value.timer
        repeat(maxAttempts) {
            delay(retryDelayMs)
            val timer = api.getTimer()
            if (timer != before) {
                _state.update { it.copy(timer = timer) }
                return
            }
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

            is TimeAction.SetVoiceVerbose -> {
                _state.value = _state.value.copy(isVoiceVerbose = action.enabled)
                viewModelScope.launch {
                    org.avmedia.gshockGoogleSync.utils.LocalDataStorage.setVoiceVerbose(appContext, action.enabled)
                }
            }

            TimeAction.StartVoiceCommand -> {
                if (voiceCommandManager.isRecognitionAvailable()) {
                    _state.value = _state.value.copy(isListening = true)

                    val startListening = {
                        viewModelScope.launch {
                            // Give a small grace period for the audio system to switch from TTS to Mic
                            delay(100)
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
                        }
                    }

                    if (_state.value.isVoiceVerbose) {
                        voiceSpeechFeedback.speak("Tell me what to do") {
                            startListening()
                        }
                    } else {
                        startListening()
                    }
                } else {
                    AppSnackbar(appContext.getString(R.string.voice_recognition_unavailable))
                }
            }

            is TimeAction.SetStepGoal -> {
                _state.value = _state.value.copy(stepGoal = action.goal)
                viewModelScope.launch {
                    scratchpadSteps.setStepGoal(action.goal)
                    scratchpadSteps.save()
                }
            }

            is TimeAction.SetWeight -> {
                val (distanceKm, calories) = calculateMetrics(_state.value.stepCounterData, action.weight)
                _state.value = _state.value.copy(
                    weight = action.weight,
                    distanceKm = distanceKm,
                    calories = calories
                )
                viewModelScope.launch {
                    scratchpadSteps.setWeight(action.weight)
                    scratchpadSteps.save()
                }
            }

            TimeAction.ClearStepHistory -> {
                viewModelScope.launch {
                    runCatching {
                        // 1. First call with peek = false to finalize transaction and clear the watch history
                        api.getStepCount(peek = false)
                        
                        // 2. Short delay to allow the watch to process the clear command
                        delay(500)

                        // 3. Second call with peek = true to read the fresh (zeroed) state
                        val stepData = api.getStepCount(peek = true)
                        
                        val (distanceKm, calories) = calculateMetrics(stepData, _state.value.weight)
                        _state.value = _state.value.copy(
                            stepCounterData = stepData,
                            distanceKm = distanceKm,
                            calories = calories
                        )
                    }.onFailure { e ->
                        AppSnackbar(e.message ?: "Api Error")
                    }
                }
            }
        }
    }

    private fun calculateMetrics(stepData: StepCounterData, weight100g: Int): Pair<Float, Float> {
        val strideM = 0.76f
        val steps = stepData.currentDaySteps ?: 0
        
        // Use distanceMeters from watch if available, otherwise estimate
        val dm = stepData.distanceMeters
        val distanceKm = if (dm != null && dm > 0) {
            dm.toFloat() / 1000f
        } else {
            (steps * strideM) / 1000f
        }
        
        val weightKg = weight100g / 10f
        val calories = distanceKm * weightKg * 1.036f
        return Pair(distanceKm, calories)
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
        eventSubscriptionName?.let { ProgressEvents.subscriber.stop(it) }
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

                val weight = scratchpadSteps.getWeight()
                val stepCounterData = if (watchFeatureManager.isFeatureSupported("time.step_counter")) {
                    if (WatchInfo.hasStepCounterMock) {
                        generateMockStepData()
                    } else {
                        api.getStepCount()
                    }
                } else StepCounterData.unavailable()

                val (distanceKm, calories) = calculateMetrics(stepCounterData, weight)

                _state.value = TimeState(
                    timer = api.getTimer(),
                    homeTime = if (watchFeatureManager.isFeatureSupported("time.home_time")) api.getHomeTime() else "",
                    batteryLevel = api.getBatteryLevel(),
                    temperature = api.getWatchTemperature(),
                    watchName = api.getWatchName(),
                    timeZoneOption = option,
                    timeOffset = offset,
                    stepCounterData = stepCounterData,
                    stepGoal = scratchpadSteps.getStepGoal(),
                    weight = weight,
                    distanceKm = distanceKm,
                    calories = calories,
                    isVoiceCommandSupported = voiceCommandManager.isRecognitionAvailable(),
                    isVoiceVerbose = org.avmedia.gshockGoogleSync.utils.LocalDataStorage.getVoiceVerbose(appContext)
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
