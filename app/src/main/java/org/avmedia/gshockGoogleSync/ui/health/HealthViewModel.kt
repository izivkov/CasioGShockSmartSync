package org.avmedia.gshockGoogleSync.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockapi.ProgressEvents
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
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
            readHealthData()
        }
    }

    fun startDataCollection(manager: HealthConnectManager) {
        healthConnectManager = manager
        scope.launch {
            readHealthData()
        }
    }

    suspend fun readHealthData() {
        val endTime = Clock.System.now().toJavaInstant()
        val startTime = endTime.minus(24, ChronoUnit.HOURS)

        runCatching {
            healthConnectManager?.getAggregatedHealthData(startTime, endTime)?.let { data ->
                _steps.value = data.steps
                _minHeartRate.value = data.minHeartRate
                _maxHeartRate.value = data.maxHeartRate
                _avgHeartRate.value = data.avgHeartRate
                _sleepDuration.value = data.sleepDurationMinutes
            }
        }.onFailure {
            ProgressEvents.onNext("HealthConnectError")
        }
    }

    suspend fun sendToHealthApp() {
        healthConnectManager?.simulateAndInsertWatchData()
    }

    private fun formatSleepDuration(durationInMinutes: Long): String {
        val hours = durationInMinutes / 60
        val minutes = durationInMinutes % 60
        return "$hours h $minutes min"
    }
}
