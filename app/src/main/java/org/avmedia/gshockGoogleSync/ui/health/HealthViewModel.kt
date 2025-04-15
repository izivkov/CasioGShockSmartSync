package org.avmedia.gshockGoogleSync.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val api: GShockRepository,
    val translateApi: TranslateRepository
) : ViewModel() {

    private val _steps = MutableStateFlow(0)
    val steps = _steps

    private val _minHeartRate = MutableStateFlow(0)
    val minHeartRate = _minHeartRate

    private val _avgHeartRate = MutableStateFlow(0)
    val avgHeartRate = _avgHeartRate

    private val _maxHeartRate = MutableStateFlow(0)
    val maxHeartRate = _maxHeartRate

    private val _sleepDuration = MutableStateFlow(0)
    val sleepDuration = _sleepDuration

    private var healthConnectManager: HealthConnectManager? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        viewModelScope.launch {
            readDataFromWatch()
        }
    }

    fun startDataCollection(manager: HealthConnectManager) {
        healthConnectManager = manager
        scope.launch {
            readDataFromWatch()
        }
    }

    private suspend fun readDataFromWatch() {
        runCatching {
            _steps.value = api.readSteps()
            _minHeartRate.value = api.readMinHeartRate()
            _maxHeartRate.value = api.readMaxHeartRate()
            _avgHeartRate.value = api.readAvgHeartRate()
            _sleepDuration.value = api.readSleepDuration()
        }.onFailure {
            ProgressEvents.onNext("ApiError")
        }
    }

    private suspend fun readHealthData() {
        healthConnectManager?.let { manager ->
            // Read steps
            _steps.value = (manager.readDailySteps() ?: 0).toInt()

            // Read heart rate
            val heartRates = manager.readHeartRateSamples()
            if (heartRates.isNotEmpty()) {
                _minHeartRate.value = heartRates.minOf { it.toInt() }
                _maxHeartRate.value = heartRates.maxOf { it.toInt() }
                _avgHeartRate.value = heartRates.average().toInt()
            }

            // Read sleep
            val sleep = manager.readSleepSessions().toInt()
            _sleepDuration.value = sleep
        }
    }

    fun sendToHealthApp () {
        // TODO
    }

    private fun formatSleepDuration(durationInMinutes: Long): String {
        val hours = durationInMinutes / 60
        val minutes = durationInMinutes % 60
        return "$hours h $minutes min"
    }
}
