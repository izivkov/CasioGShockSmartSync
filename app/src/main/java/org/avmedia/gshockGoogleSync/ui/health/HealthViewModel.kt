package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    val translateApi: TranslateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())

    var permissionsGranted by mutableStateOf(false)

    val permissionsLauncher =
        healthConnectManager
            .requestPermissionsActivityContract()

    private fun checkPermissions() {
        viewModelScope.launch {
            permissionsGranted =
                healthConnectManager
                    .hasAllPermissions()
        }
    }

    init {
        loadHealthData()
    }

    private fun loadHealthData() {
        viewModelScope.launch {
            val endTime = ZonedDateTime.now()
            val startTime = endTime.minusDays(1)

            try {
                // Get steps
                val steps = healthConnectManager.getSteps(
                    startTime.toInstant(),
                    endTime.toInstant()
                )

                // read this for HealthConnectManager
                val healthData = HealthUiState(
                    steps = "1000",
                    minHeartRate = "65",
                    maxHeartRate = "93",
                    avgHeartRate = "70",
                    sleepDuration = "1.4"
                )

                _uiState.value = HealthUiState(
                    steps = healthData.steps,
                    minHeartRate = healthData.minHeartRate,
                    maxHeartRate = healthData.maxHeartRate,
                    avgHeartRate = healthData.avgHeartRate,
                    sleepDuration = healthData.sleepDuration
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

data class HealthUiState(
    val steps: String = "0",
    val minHeartRate: String = "--",
    val maxHeartRate: String = "--",
    val avgHeartRate: String = "--",
    val sleepDuration: String = "No data"
)
