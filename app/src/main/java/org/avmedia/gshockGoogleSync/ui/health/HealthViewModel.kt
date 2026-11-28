package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogSource
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
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
// The DW-H5600 does not use the ABL-100 exercise profile. These average values stand in until
// the watch's own profile is decoded. The step goal comes from the watch, not from this profile.
private val H5600_AVERAGE_PROFILE = ExerciseProfile(stepGoal = 8000, height = 170, weight = 70)

// The exercise profile is read after the steps, and must not hold up the screen.
private val PROFILE_READ_TIMEOUT = 10.seconds
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

/** The DW-H5600 step goal comes from the watch and overrides the profile value. */
internal fun HealthState.withWatchGoal(stepData: StepCounterData): HealthState =
    stepData.h5600?.stepGoal?.let { copy(stepGoal = it) } ?: this

internal fun HealthState.withStepMetrics(stepData: StepCounterData, weightKg: Int = weight): HealthState {
    val (distanceKm, calories) = calculateStepMetrics(stepData, weightKg)
    return copy(distanceKm = distanceKm, calories = calories)
}

data class HealthState(
    val stepCounterData: StepCounterData = StepCounterData.unavailable(),
    /** True while the DW-H5600 LifeLog is being read from the watch. */
    val lifeLogLoading: Boolean = false,
    /** True while the latest data is being read; the screen shows a small "Updating..." line. */
    val updating: Boolean = false,
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

    /** The screen was opened: shows the stored day and starts a read by itself ([HealthOpenRead]). */
    data object ScreenOpened : HealthAction
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
        showStoredHistory()
        setupEventSubscription()

    }

    /** The Health screen is on screen: poll while it stays open. */
    fun onScreenShown() {
        if (WatchInfo.hasStepCounter || WatchInfo.hasStepCounterMock) startStepCounterPolling()
    }

    /** The Health screen is off screen: stop polling, so no LifeLog reads start from a screen you are not viewing. */
    fun onScreenHidden() {
        stepPollJob?.cancel()
        stepPollJob = null
    }

    /** DW-H5600: show the last stored day at once; a running or later read replaces it. Nothing stored: the screen stays as it was. */
    private fun showStoredHistory() {
        if (!isDwH5600()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val stored = runCatching {
                val dao = org.avmedia.gshockGoogleSync.history.HistoryDb.get(appContext).dao()
                org.avmedia.gshockGoogleSync.history.StoredHealth.stepData(dao.allDays(), org.avmedia.gshockGoogleSync.history.LastReadStore.load(appContext), hrOfDay = { dao.hrSlotsOf(it) }) { dao.hoursOf(it) }
            }.onFailure { Timber.w(it, "Health: reading the stored history failed"); AppLog.failure(LogSource.Health, "Reading the stored history failed", it) }.getOrNull() ?: return@launch
            _state.update { current ->
                if (current.stepCounterData.h5600 != null) current
                else current.copy(stepCounterData = stored, lifeLogLoading = false).withStepMetrics(stored).withWatchGoal(stored)
            }
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
        if (isDwH5600()) H5600_AVERAGE_PROFILE
        else runCatching { api.getExerciseProfile() }.getOrNull()

    suspend fun saveExerciseProfile(profile: ExerciseProfile): Boolean =
        if (isDwH5600()) false else runCatching {
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
            HealthAction.ScreenOpened -> onOpened()

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

    private suspend fun fetchStepCounter(force: Boolean): StepCounterData {
        if (WatchInfo.hasStepCounterMock) return generateMockStepData()
        if (!isDwH5600()) return readSteps(force)
        // DW-H5600: what this connection has read is shown as it is; only a forced read (the first open) goes to the watch.
        if (!force) H5600Session.data?.let { return it }
        // One transfer at a time, shared with the plugins' reads.
        if (!H5600Session.inFlight.compareAndSet(false, true)) {
            return H5600Session.data ?: StepCounterData.unavailable()
        }
        try {
            return api.getStepCount().let { if (it.h5600 != null) H5600Session.store(it) else it }
        } finally {
            H5600Session.inFlight.set(false)
        }
    }

    /**
     * The ABL-100: the steps of this connection are kept in [StepSession]. Only a forced read (the open, the polling) asks the
     * watch; a read that comes back empty is logged and the last good steps stay, so a failed read never blanks the screen.
     */
    private suspend fun readSteps(force: Boolean): StepCounterData {
        if (!force) StepSession.data?.let { return it }
        val read = api.getStepCount()
        if (hasNoStepData(read)) {
            Timber.w("Health: the step read returned no data")
            AppLog.failure(LogSource.Health, "The step read returned no data${if (StepSession.data != null) " (showing the last read)" else ""}")
            return StepSession.data ?: read
        }
        StepSession.store(read)
        return read
    }

    /**
     * The screen opened: the DW-H5600 shows what this connection has read already, with no loading and no watch transfer;
     * the first open of a connection, and the other watches, read the latest data (the LifeLog, the steps of the ABL-100).
     */
    private fun onOpened() {
        val supported = WatchInfo.hasStepCounter && watchFeatureManager.isFeatureSupported(FEATURE_STEP_COUNTER)
        if (!supported) return
        if (isDwH5600()) {
            H5600Session.data?.let {
                showSessionData(it)
                return
            }
        } else {
            // The steps of this connection are shown at once; the read below (not within a minute of the last) refreshes them.
            StepSession.data?.let { cached ->
                _state.update { current -> current.copy(stepCounterData = cached).withStepMetrics(cached).withWatchGoal(cached) }
            }
        }
        if (HealthOpenRead.shared.tryStart()) requestStepRefresh(force = true) else refreshState()
    }

    /** Shows data that is in hand. The DW-H5600 has a fixed exercise profile, so nothing here waits for the watch. */
    private fun showSessionData(stepData: StepCounterData) {
        Timber.i("Health: showing the data of this connection (steps=${stepData.currentDaySteps})")
        _state.update { current ->
            current.copy(stepCounterData = stepData, lifeLogLoading = false).withStepMetrics(stepData).withWatchGoal(stepData)
        }
        viewModelScope.launch {
            readExerciseProfile()?.let { profile ->
                _state.update { current -> applyExerciseProfileState(current, profile, stepData = stepData).withWatchGoal(stepData) }
            }
        }
    }

    private fun requestStepRefresh(force: Boolean = false) {
        Timber.i("Health: step refresh requested (running=${stepFetchJob?.isActive == true}, dwH5600=${isDwH5600()})")
        if (stepFetchJob?.isActive == true) {
            stepFetchPending = true
            return
        }
        _state.update { it.copy(updating = true) }
        stepFetchJob = viewModelScope.launch {
            do {
                stepFetchPending = false
                runCatching {
                    val timeout = if (isDwH5600()) LIFELOG_FETCH_TIMEOUT else STEP_FETCH_TIMEOUT
                    val needsWatch = isDwH5600() && (force || H5600Session.data == null)
                    if (needsWatch) _state.update { it.copy(lifeLogLoading = true) }
                    Timber.i("Health: fetching step data (timeout=$timeout, fromWatch=$needsWatch)")
                    val stepData = withTimeoutOrNull(timeout) { fetchStepCounter(force) }
                    if (stepData == null) {
                        Timber.w("Health: step fetch returned nothing (timed out or cancelled)")
                        AppLog.failure(LogSource.Health, "LifeLog step fetch returned nothing (timed out or cancelled)")
                        _state.update { it.copy(lifeLogLoading = false) }
                        return@runCatching
                    }
                    Timber.i("Health: step data received, steps=${stepData.currentDaySteps}, h5600=${stepData.h5600 != null}")
                    // Show the step data first. The exercise profile is read afterwards, with a
                    // timeout: after a DW-H5600 transfer the watch may not answer that request, and
                    // the screen must not wait for it.
                    _state.update { current ->
                        current.copy(stepCounterData = stepData, lifeLogLoading = false)
                            .withStepMetrics(stepData)
                            .withWatchGoal(stepData)
                    }

                    val profile = withTimeoutOrNull(PROFILE_READ_TIMEOUT) { readExerciseProfile() }
                    if (profile != null) {
                        _state.update { current ->
                            applyExerciseProfileState(current, profile, stepData = stepData)
                                .withWatchGoal(stepData)
                        }
                    }
                }.onFailure {
                    Timber.w(it, "Health: step refresh failed")
                    AppLog.failure(LogSource.Health, "LifeLog step refresh failed", it)
                    _state.update { current -> current.copy(lifeLogLoading = false) }
                }
            } while (stepFetchPending)
            _state.update { it.copy(updating = false) }
        }
    }

    private fun isDwH5600(): Boolean = WatchInfo.model == WatchInfo.WatchModel.DW_H5600

    private fun startStepCounterPolling() {
        stepPollJob?.cancel()
        stepPollJob = viewModelScope.launch {
            while (isActive) {
                delay(if (isDwH5600()) LIFELOG_POLL_INTERVAL else STEP_POLL_INTERVAL)
                if (watchFeatureManager.isFeatureSupported(FEATURE_STEP_COUNTER)) {
                    requestStepRefresh(force = !isDwH5600())
                }
            }
        }
    }

    /** Shows what is known: the session's data when fresh, a read only when there is none. */
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