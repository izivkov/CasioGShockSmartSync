package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    val translateApi: TranslateRepository
) : ViewModel() {

    var steps by mutableStateOf(0)
        private set
    var minHeartRate by mutableStateOf(0)
        private set
    var avgHeartRate by mutableStateOf(0)
        private set
    var maxHeartRate by mutableStateOf(0)
        private set
    var sleepDuration by mutableStateOf("")
        private set

    private var healthConnectManager: HealthConnectManager? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startDataCollection(manager: HealthConnectManager) {
        healthConnectManager = manager
        scope.launch {
            readHealthData()
        }
    }

    private suspend fun readHealthData() {
        healthConnectManager?.let { manager ->
            // Read steps
            steps = (manager.readDailySteps() ?: 0).toInt()

            // Read heart rate
            val heartRates = manager.readHeartRateSamples()
            if (heartRates.isNotEmpty()) {
                minHeartRate = heartRates.minOf { it.toInt() }
                maxHeartRate = heartRates.maxOf { it.toInt() }
                avgHeartRate = heartRates.average().toInt()
            }

            // Read sleep
            val sleep = manager.readSleepSessions()
            sleepDuration = formatSleepDuration(sleep)
        }
    }

    private fun formatSleepDuration(durationInMinutes: Long): String {
        val hours = durationInMinutes / 60
        val minutes = durationInMinutes % 60
        return "$hours h $minutes min"
    }
}
