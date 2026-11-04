package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.subscribeToProgressEvents
import org.avmedia.gshockGoogleSync.voice.VoiceCommandManager
import org.avmedia.gshockGoogleSync.voice.VoiceDispatcher
import org.avmedia.gshockGoogleSync.voice.VoiceSpeechFeedback
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.model.StepCounterData
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class StepDataOption {
    TODAY, HOURLY, DAILY
}

private const val FEATURE_STEP_COUNTER = "time.step_counter"
private const val FEATURE_HOME_TIME = "time.home_time"
private val STEP_POLL_INTERVAL = 5.minutes
private val STEP_FETCH_TIMEOUT = 8.seconds

internal fun resolveDistanceKm(stepData: StepCounterData, strideMeters: Float = 0.73f): Float {
    val steps = stepData.currentDaySteps ?: 0
    val rawDistanceMeters = stepData.distanceMeters

    if (rawDistanceMeters != null && rawDistanceMeters > 0) {
        val minReasonableMeters = (steps * 0.4f).toInt()
        val maxReasonableMeters = (steps * 0.85f).toInt()
        if (rawDistanceMeters in minReasonableMeters..maxReasonableMeters) {
            return rawDistanceMeters.toFloat() / 1000f
        }
    }

    return (steps * strideMeters) / 1000f
}

internal fun hasNoStepData(stepData: StepCounterData): Boolean =
    stepData.currentDaySteps == null &&
            stepData.distanceMeters == null &&
            stepData.totalDistanceMeters == null

internal fun calculateStepMetrics(stepData: StepCounterData, weight100g: Int): Pair<Float, Float> {
    val distanceKm = resolveDistanceKm(stepData)
    val weightKg = weight100g / 10f
    return distanceKm to distanceKm * weightKg * 1.036f
}

/** Recomputes distance and calories from step data. Weight defaults to the state's current weight. */
internal fun TimeState.withStepMetrics(stepData: StepCounterData, weight100g: Int = weight): TimeState {
    val (distanceKm, calories) = calculateStepMetrics(stepData, weight100g)
    return copy(distanceKm = distanceKm, calories = calories)
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
    val isVoiceVerbose: Boolean = true,
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

internal fun applyExerciseProfileState(
    current: TimeState,
    profile: ExerciseProfile,
    weight100g: Int = profile.weight * 10,
    stepData: StepCounterData = current.stepCounterData
): TimeState {
    val updated = current.copy(stepGoal = profile.stepGoal, weight = weight100g)
    return if (hasNoStepData(stepData)) updated else updated.withStepMetrics(stepData, weight100g)
}

internal fun resolveConfirmedExerciseProfile(
    written: ExerciseProfile,
    fresh: ExerciseProfile?
): ExerciseProfile = fresh ?: written

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
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TimeState())
    val state = _state.asStateFlow()

    private var eventSubscriptionName: String? = null
    private var saveJob: Job? = null
    private var stepPollJob: Job? = null
    private var stepFetchJob: Job? = null
    private var stepFetchPending = false
    private var exerciseProfileCache: ExerciseProfile = defaultExerciseProfile()
    private var exerciseProfileInitialized = false

    init {
        refreshState()
        setupEventSubscription()

        if (WatchInfo.hasStepCounter || WatchInfo.hasStepCounterMock) {
            startStepCounterPolling()
        }
    }

    private fun setupEventSubscription() {
        // ProgressEvents drops a second subscription registered under a name it has
        // already seen, so each instance uses a unique name.
        eventSubscriptionName = subscribeToProgressEvents(
            "TimeViewModel",
            arrayOf(
                EventAction("TimerUpdated") {
                    val payload = ProgressEvents.getPayload("TimerUpdated") as? Int
                    if (payload != null) {
                        _state.update { it.copy(timer = payload) }
                    } else {
                        refreshState()
                    }
                },
                EventAction("ExerciseProfileUpdated") {
                    val payload = ProgressEvents.getPayload("ExerciseProfileUpdated") as? ExerciseProfile
                    if (payload != null) {
                        _state.update { applyExerciseProfileState(it, payload) }
                    } else {
                        refreshState()
                    }
                }
            )
        )
    }

    private fun defaultExerciseProfile(): ExerciseProfile {
        val country = Locale.getDefault().country.uppercase()
        val isImperial = country in setOf("US", "LR", "MM", "CA")
        return ExerciseProfile(
            stepGoal = 10000,
            height = 170,
            weight = if (isImperial) 70 else 65
        )
    }

    suspend fun getExerciseProfileOrDefault(): ExerciseProfile {
        if (exerciseProfileInitialized) {
            return exerciseProfileCache
        }

        val freshProfile = runCatching { api.getExerciseProfile() }.getOrNull()
        val profile = freshProfile ?: exerciseProfileCache
        exerciseProfileCache = profile
        exerciseProfileInitialized = true
        return profile
    }

    suspend fun saveExerciseProfile(profile: ExerciseProfile): Boolean =
        runCatching {
            val setProfileAction =
                actionContainer.getAction(ActionContainer.SetExerciseProfileAction::class.java)
            if (!setProfileAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                return false
            }

            val confirmedProfile = setProfileAction.runWithProfile(appContext, profile).getOrThrow()
            exerciseProfileCache = confirmedProfile
            exerciseProfileInitialized = true

            _state.update { applyExerciseProfileState(it, confirmedProfile) }
            true
        }.getOrDefault(false)

    fun onAction(action: TimeAction) {
        when (action) {
            is TimeAction.SetTimer -> _state.update {
                it.copy(timer = action.hours * 3600 + action.minutes * 60 + action.seconds)
            }

            is TimeAction.UpdateTimer -> viewModelScope.launch {
                val setTimerAction =
                    actionContainer.getAction(ActionContainer.SetTimerAction::class.java)
                if (setTimerAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                    setTimerAction.runWithTimer(appContext, action.timeMs)
                }
            }

            TimeAction.SendTimeToWatch -> viewModelScope.launch {
                runCatching {
                    AppSnackbar(appContext.getString(R.string.sending_time_to_watch))
                    watchTimeUpdater.updateTime()
                    AppSnackbar(appContext.getString(R.string.time_set))
                    refreshState()
                }.onFailure { e ->
                    AppSnackbar(e.message ?: "Api Error")
                }
            }

            TimeAction.RefreshState -> refreshState()

            is TimeAction.SetStepDataOption -> _state.update {
                it.copy(selectedStepDataOption = action.option)
            }

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

            is TimeAction.SetStepGoal -> saveProfileChange { it.copy(stepGoal = action.goal) }

            is TimeAction.SetWeight -> saveProfileChange { it.copy(weight = action.weight / 10) }

            TimeAction.ClearStepHistory -> viewModelScope.launch {
                val clearAction =
                    actionContainer.getAction(ActionContainer.ClearStepHistoryAction::class.java)
                if (!clearAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                    return@launch
                }

                clearAction.runAndGetFreshData()
                    .onSuccess { stepData ->
                        _state.update { it.copy(stepCounterData = stepData).withStepMetrics(stepData) }
                    }
                    .onFailure { e ->
                        AppSnackbar(e.message ?: "Api Error")
                    }
            }
        }
    }

    /** Applies a change to the exercise profile and saves it to the watch. */
    private fun saveProfileChange(change: (ExerciseProfile) -> ExerciseProfile) {
        viewModelScope.launch {
            saveExerciseProfile(change(getExerciseProfileOrDefault()))
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

    private suspend fun fetchStepCounter(): StepCounterData =
        if (WatchInfo.hasStepCounterMock) generateMockStepData() else api.getStepCount()

    /**
     * Starts a step fetch unless one is already running. A request that arrives
     * mid-fetch sets a flag, and the running coroutine fetches once more afterward,
     * so the final state always reflects the latest request.
     */
    private fun requestStepRefresh() {
        if (stepFetchJob?.isActive == true) {
            stepFetchPending = true
            return
        }
        stepFetchJob = viewModelScope.launch {
            do {
                stepFetchPending = false
                runCatching {
                    val profile = getExerciseProfileOrDefault()
                    val stepData = withTimeoutOrNull(STEP_FETCH_TIMEOUT) { fetchStepCounter() }
                        ?: return@runCatching

                    _state.update { current ->
                        applyExerciseProfileState(
                            current.copy(stepCounterData = stepData),
                            profile,
                            stepData = stepData
                        )
                    }
                }
            } while (stepFetchPending)
        }
    }

    private fun startStepCounterPolling() {
        stepPollJob?.cancel()
        stepPollJob = viewModelScope.launch {
            while (isActive) {
                delay(STEP_POLL_INTERVAL)
                if (watchFeatureManager.isFeatureSupported(FEATURE_STEP_COUNTER)) {
                    requestStepRefresh()
                }
            }
        }
    }

    private fun calculateOffset(option: TimeSettingsStorage.TimeZoneOption): Long =
        SolarTimeHelper.calculateTimeOffset(appContext, option)

    override fun onCleared() {
        eventSubscriptionName?.let { ProgressEvents.subscriber.stop(it) }
        stepPollJob?.cancel()
        saveJob?.let {
            saveJob?.cancel()
            viewModelScope.launch {
                timeSettingsStorage.save()
            }
        }
    }

    /**
     * Publishes the fast fields first. Step data is requested separately, so a
     * slow step read no longer holds up the rest of the screen.
     */
    private fun refreshState() {
        viewModelScope.launch {
            runCatching {
                timeSettingsStorage.load()
                val option = timeSettingsStorage.getTimeZoneOption()
                val offset = calculateOffset(option)

                val timer = api.getTimer()
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

            if (WatchInfo.hasStepCounter && watchFeatureManager.isFeatureSupported(FEATURE_STEP_COUNTER)) {
                requestStepRefresh()
            }
        }
    }

    private fun generateMockStepData(): StepCounterData {
        val hourly = List(144) { index ->
            val hour = index / 6
            if (hour in 7..22) Random.nextInt(20, 350) else 0
        }

        val daily = List(14) { Random.nextInt(4000, 12500) }

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