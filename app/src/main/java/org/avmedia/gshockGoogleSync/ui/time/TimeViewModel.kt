package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.session.ComponentId
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.voice.VoiceCommandManager
import org.avmedia.gshockGoogleSync.voice.VoiceDispatcher
import org.avmedia.gshockGoogleSync.voice.VoiceSpeechFeedback
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject

private const val FEATURE_HOME_TIME = "time.home_time"

data class TimeState(
    val timer: Int = 0,
    val homeTime: String = "",
    val batteryLevel: Int = 0,
    val temperature: Int = 0,
    val watchName: String = "",
    val timeZoneOption: TimeSettingsStorage.TimeZoneOption = TimeSettingsStorage.TimeZoneOption.SYSTEM,
    val timeOffset: Long = 0L,
    val isListening: Boolean = false,
    val isVoiceCommandSupported: Boolean = false,
    val isVoiceVerbose: Boolean = true,
    val isSendingTime: Boolean = false,
)

sealed interface TimeAction {
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : TimeAction
    data class UpdateTimer(val timeMs: Int) : TimeAction
    data object SendTimeToWatch : TimeAction
    data object RefreshState : TimeAction
    data class SetTimeZoneOption(val option: TimeSettingsStorage.TimeZoneOption) : TimeAction
    data class SetVoiceVerbose(val enabled: Boolean) : TimeAction
    data object StartVoiceCommand : TimeAction
}

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val api: GShockRepository,
    private val timeSettingsStorage: TimeSettingsStorage,
    private val watchTimeUpdater: WatchTimeUpdater,
    private val watchFeatureManager: IWatchFeatureManager,
    private val voiceCommandManager: VoiceCommandManager,
    private val voiceDispatcher: VoiceDispatcher,
    private val voiceSpeechFeedback: VoiceSpeechFeedback,
    private val actionContainer: ActionContainer,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimeState())
    val state = _state.asStateFlow()

    /** True while the timer was changed in the app and not yet sent to the watch. */
    val isTimerModified: StateFlow<Boolean> = sessionManager.isPendingSaveFlow(ComponentId.TIMER)
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.isPendingSave(ComponentId.TIMER))

    private var eventSubscription: AutoCloseable? = null
    private var saveJob: Job? = null

    init {
        refreshState()
        setupEventSubscription()
    }

    private fun setupEventSubscription() {
        eventSubscription = ProgressEvents.subscribe(
            arrayOf(
                EventAction("TimerUpdated") {
                    val payload = ProgressEvents.getPayload("TimerUpdated") as? Int
                    if (payload != null) {
                        _state.update { it.copy(timer = payload) }
                        sessionManager.clearPendingSave(ComponentId.TIMER)
                    } else {
                        refreshState()
                    }
                }
            )
        )
    }

    fun onAction(action: TimeAction) {
        when (action) {
            is TimeAction.SetTimer -> {
                val newTimer = action.hours * 3600 + action.minutes * 60 + action.seconds
                if (newTimer != _state.value.timer) {
                    _state.update { it.copy(timer = newTimer) }
                    sessionManager.markPendingSave(ComponentId.TIMER)
                }
            }

            is TimeAction.UpdateTimer -> viewModelScope.launch {
                val setTimerAction =
                    actionContainer.getAction(ActionContainer.SetTimerAction::class.java)
                if (setTimerAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                    // Disable the Send button right away; it is re-enabled only if the send fails.
                    sessionManager.clearPendingSave(ComponentId.TIMER)
                    if (!setTimerAction.runWithTimer(appContext, action.timeMs)) {
                        sessionManager.markPendingSave(ComponentId.TIMER)
                    }
                }
            }

            TimeAction.SendTimeToWatch -> {
                if (_state.value.isSendingTime) return
                _state.update { it.copy(isSendingTime = true) }
                viewModelScope.launch {
                    runCatching {
                        watchTimeUpdater.updateTime()
                        readState() // wait for the read-back, so the button stays disabled until it's done
                    }.onFailure { e ->
                        AppSnackbar(e.message ?: "Api Error")
                    }
                    // The button stays disabled until the time is set and read back.
                    _state.update { it.copy(isSendingTime = false) }
                }
            }

            TimeAction.RefreshState -> refreshState()

            is TimeAction.SetTimeZoneOption -> {
                val offset = calculateOffset(action.option)
                _state.update { it.copy(timeZoneOption = action.option, timeOffset = offset) }
                timeSettingsStorage.setTimeZoneOption(action.option)

                saveJob?.cancel()
                saveJob = viewModelScope.launch {
                    delay(0)
                    timeSettingsStorage.save()
                    saveJob = null
                }
            }

            is TimeAction.SetVoiceVerbose -> {
                _state.update { it.copy(isVoiceVerbose = action.enabled) }
                viewModelScope.launch {
                    LocalDataStorage.setVoiceVerbose(appContext, action.enabled)
                }
            }

            TimeAction.StartVoiceCommand -> startVoiceCommand()
        }
    }

    private fun startVoiceCommand() {
        if (!voiceCommandManager.isRecognitionAvailable()) {
            AppSnackbar(appContext.getString(R.string.voice_recognition_unavailable))
            return
        }

        _state.update { it.copy(isListening = true) }

        val startListening = {
            viewModelScope.launch {
                // Grace period for the audio system to switch from TTS to mic
                delay(100)
                voiceCommandManager.startListening(
                    onResult = { text ->
                        _state.update { it.copy(isListening = false) }
                        voiceDispatcher.dispatch(text)
                    },
                    onError = { error ->
                        _state.update { it.copy(isListening = false) }
                        AppSnackbar(error)
                    }
                )
            }
        }

        if (_state.value.isVoiceVerbose) {
            voiceSpeechFeedback.speak("Tell me what to do or say 'Help'") { startListening() }
        } else {
            startListening()
        }
    }

    private fun calculateOffset(option: TimeSettingsStorage.TimeZoneOption): Long =
        SolarTimeHelper.calculateTimeOffset(appContext, option)

    override fun onCleared() {
        eventSubscription?.close()
        saveJob?.let {
            saveJob?.cancel()
            viewModelScope.launch {
                timeSettingsStorage.save()
            }
        }
    }

    private fun refreshState() {
        viewModelScope.launch { readState() }
    }

    private suspend fun readState() {
        runCatching {
            timeSettingsStorage.load()
            val option = timeSettingsStorage.getTimeZoneOption()
            val offset = calculateOffset(option)

            val timer = api.getTimer()
            sessionManager.clearPendingSave(ComponentId.TIMER)
            val homeTime = if (watchFeatureManager.isFeatureSupported(FEATURE_HOME_TIME)) api.getHomeTime() else ""
            val batteryLevel = api.getBatteryLevel()
            val temperature = api.getWatchTemperature()
            val watchName = api.getWatchName()
            val isVoiceCommandSupported = voiceCommandManager.isRecognitionAvailable()
            val isVoiceVerbose = LocalDataStorage.getVoiceVerbose(appContext)

            _state.update {
                it.copy(
                    timer = timer,
                    homeTime = homeTime,
                    batteryLevel = batteryLevel,
                    temperature = temperature,
                    watchName = watchName,
                    timeZoneOption = option,
                    timeOffset = offset,
                    isVoiceCommandSupported = isVoiceCommandSupported,
                    isVoiceVerbose = isVoiceVerbose
                )
            }
        }.onFailure {
            AppSnackbar("Api Error")
        }
    }
}