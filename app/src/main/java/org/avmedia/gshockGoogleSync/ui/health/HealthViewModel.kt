package org.avmedia.gshockGoogleSync.ui.health

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
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.model.StepCounterData
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class StepDataOption {
    TODAY, HOURLY, DAILY
}

private const val FEATURE_STEP_COUNTER = "time.step_counter"
private val STEP_POLL_INTERVAL = 5.minutes
private val STEP_FETCH_TIMEOUT = 8.seconds
// A DW-H5600 LifeLog transfer takes 30 to 100 seconds, so it needs a longer timeout and a
// slower poll than the ABL-100 step read.
private val LIFELOG_FETCH_TIMEOUT = 3.minutes
private val LIFELOG_POLL_INTERVAL = 15.minutes
private val IMPERIAL_COUNTRIES = setOf("US", "CA")

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

internal fun calculateStepMetrics(stepData: StepCounterData, weightKg: Int): Pair<Float, Float> {
    val distanceKm = resolveDistanceKm(stepData)
    return distanceKm to distanceKm * weightKg * 1.036f
}

internal fun HealthState.withStepMetrics(stepData: StepCounterData, weightKg: Int = weight): HealthState {
    val (distanceKm, calories) = calculateStepMetrics(stepData, weightKg)
    return copy(distanceKm = distanceKm, calories = calories)
}

data class HealthState(
    val stepCounterData: StepCounterData = StepCounterData.unavailable(),
    val selectedStepDataOption: StepDataOption = StepDataOption.TODAY,
    val stepGoal: Int = 10000,
    val weight: Int = 70, // kg
    val height: Int = 170, // cm
    val calories: Float = 0f,
    val distanceKm: Float = 0f
)

sealed interface HealthAction {
    data class SetStepDataOption(val option: StepDataOption) : HealthAction
    data class SetStepGoal(val goal: Int) : HealthAction
    data class SetWeight(val kg: Int) : HealthAction
    data class SetWeightPounds(val pounds: Double) : HealthAction
    data class SetHeight(val heightCm: Int) : HealthAction
    data class SetHeightInches(val inches: Double) : HealthAction
    data object ClearStepHistory : HealthAction
    data object RefreshState : HealthAction
}

internal fun applyExerciseProfileState(
    current: HealthState,
    profile: ExerciseProfile,
    stepData: StepCounterData = current.stepCounterData
): HealthState {
    val updated = current.copy(
        stepGoal = profile.stepGoal,
        weight = profile.weight,
        height = profile.height
    )
    return if (hasNoStepData(stepData)) updated else updated.withStepMetrics(stepData, profile.weight)
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val api: GShockRepository,
    private val watchFeatureManager: IWatchFeatureManager,
    private val actionContainer: ActionContainer,
    private val sessionManager: org.avmedia.gshockGoogleSync.session.SessionManager,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HealthState())
    val state = _state.asStateFlow()

    val usesImperialUnits: Boolean =
        Locale.getDefault().country.uppercase(Locale.US) in IMPERIAL_COUNTRIES

    private var eventSubscription: AutoCloseable? = null
    private var stepPollJob: Job? = null
    private var stepFetchJob: Job? = null
    private var stepFetchPending = false

    init {
        refreshState()
        setupEventSubscription()

        if (WatchInfo.hasStepCounter || WatchInfo.hasStepCounterMock) {
            startStepCounterPolling()
        }
    }

    private fun setupEventSubscription() {
        eventSubscription = ProgressEvents.subscribe(
            arrayOf(
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

    suspend fun readExerciseProfile(): ExerciseProfile? =
        runCatching { api.getExerciseProfile() }.getOrNull()

    suspend fun saveExerciseProfile(profile: ExerciseProfile): Boolean =
        runCatching {
            val setProfileAction =
                actionContainer.getAction(ActionContainer.SetExerciseProfileAction::class.java)
            if (!setProfileAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                return false
            }

            val confirmedProfile = setProfileAction.runWithProfile(appContext, profile).getOrThrow()
            _state.update { applyExerciseProfileState(it, confirmedProfile) }
            sessionManager.clearPendingSave(org.avmedia.gshockGoogleSync.session.ComponentId.HEALTH_PROFILE)
            true
        }.getOrDefault(false)

    fun heightInputText(cm: Int): String =
        if (usesImperialUnits) String.format(Locale.US, "%.1f", ExerciseProfile(height = cm).heightInches)
        else cm.toString()

    fun weightInputText(kg: Int): String =
        if (usesImperialUnits) String.format(Locale.US, "%.1f", ExerciseProfile(weight = kg).weightPounds)
        else kg.toString()

    fun onAction(action: HealthAction) {
        when (action) {
            is HealthAction.SetStepDataOption -> _state.update {
                it.copy(selectedStepDataOption = action.option)
            }

            is HealthAction.SetStepGoal -> saveProfileChange { it.copy(stepGoal = action.goal) }

            is HealthAction.SetWeight -> saveProfileChange { it.copy(weight = action.kg) }

            is HealthAction.SetWeightPounds -> saveProfileChange { it.withWeightPounds(action.pounds) }

            is HealthAction.SetHeight -> saveProfileChange { it.copy(height = action.heightCm) }

            is HealthAction.SetHeightInches -> saveProfileChange { it.withHeightInches(action.inches) }

            HealthAction.ClearStepHistory -> viewModelScope.launch {
                val clearAction =
                    actionContainer.getAction(ActionContainer.ClearStepHistoryAction::class.java)
                if (!clearAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                    return@launch
                }

                clearAction.runAndGetFreshData()
                    .onSuccess { stepData ->
                        _state.update { it.copy(stepCounterData = stepData).withStepMetrics(stepData) }
                    }
                    .onFailure {
                        // Handled by action or UI
                    }
            }

            HealthAction.RefreshState -> refreshState()
        }
    }

    private fun saveProfileChange(change: (ExerciseProfile) -> ExerciseProfile) {
        viewModelScope.launch {
            val current = readExerciseProfile() ?: return@launch
            saveExerciseProfile(change(current))
        }
    }

    private suspend fun fetchStepCounter(): StepCounterData =
        if (WatchInfo.hasStepCounterMock) generateMockStepData() else api.getStepCount()

    private fun requestStepRefresh() {
        Timber.i("Health: step refresh requested (running=${stepFetchJob?.isActive == true}, dwH5600=${isDwH5600()})")
        if (stepFetchJob?.isActive == true) {
            stepFetchPending = true
            return
        }
        stepFetchJob = viewModelScope.launch {
            do {
                stepFetchPending = false
                runCatching {
                    val timeout = if (isDwH5600()) LIFELOG_FETCH_TIMEOUT else STEP_FETCH_TIMEOUT
                    Timber.i("Health: fetching step data (timeout=$timeout)")
                    val stepData = withTimeoutOrNull(timeout) { fetchStepCounter() }
                    if (stepData == null) {
                        Timber.w("Health: step fetch returned nothing (timed out or cancelled)")
                        return@runCatching
                    }
                    Timber.i("Health: step data received, steps=${stepData.currentDaySteps}, h5600=${stepData.h5600 != null}")
                    val profile = readExerciseProfile()

                    _state.update { current ->
                        val withSteps = current.copy(stepCounterData = stepData)
                        val updated =
                            if (profile != null) {
                                applyExerciseProfileState(withSteps, profile, stepData = stepData)
                            } else {
                                withSteps.withStepMetrics(stepData)
                            }
                        // The watch's own step goal is authoritative for the DW-H5600.
                        stepData.h5600?.stepGoal?.let { updated.copy(stepGoal = it) } ?: updated
                    }
                }
            } while (stepFetchPending)
        }
    }

    private fun isDwH5600(): Boolean = WatchInfo.model == WatchInfo.WatchModel.DW_H5600

    private fun startStepCounterPolling() {
        stepPollJob?.cancel()
        stepPollJob = viewModelScope.launch {
            while (isActive) {
                delay(if (isDwH5600()) LIFELOG_POLL_INTERVAL else STEP_POLL_INTERVAL)
                if (watchFeatureManager.isFeatureSupported(FEATURE_STEP_COUNTER)) {
                    requestStepRefresh()
                }
            }
        }
    }

    private fun refreshState() {
        viewModelScope.launch {
            val supported = WatchInfo.hasStepCounter && watchFeatureManager.isFeatureSupported(FEATURE_STEP_COUNTER)
            Timber.i("Health: refreshState (hasStepCounter=${WatchInfo.hasStepCounter}, feature supported=$supported)")
            if (supported) {
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
            currentDaySteps = Random.nextInt(8000, 9500)
        )
    }

    override fun onCleared() {
        eventSubscription?.close()
        stepPollJob?.cancel()
    }
}