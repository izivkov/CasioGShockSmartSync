package org.avmedia.gShockSmartSyncCompose.ui.alarms

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.api
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.applicationContext
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ProgressEvents
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AlarmViewModel : ViewModel() {
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms

    init {
        loadAlarms()
    }

    private fun loadAlarms() {
        viewModelScope.launch {
            try {
                // Load the alarms initially
                val loadedAlarms = api().getAlarms() // Call your suspend function here
                _alarms.value = loadedAlarms
                AlarmsModel.clear()
                AlarmsModel.addAll(ArrayList(_alarms.value))
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError")
            }
        }
    }

    fun toggleAlarm(index: Int, isEnabled: Boolean) {
        val updatedAlarms = _alarms.value.toMutableList()
        updatedAlarms[index].enabled = isEnabled
        _alarms.value = updatedAlarms
    }

    fun onTimeChanged(index: Int, hours: Int, minutes: Int) {
        val updatedAlarms = _alarms.value.toMutableList()
        updatedAlarms[index].hour = hours
        updatedAlarms[index].minute = minutes
        _alarms.value = updatedAlarms
    }

    fun sendAlarmsToWatch() {
        viewModelScope.launch {
            try {
                api().setAlarms(alarms = ArrayList(alarms.value))
                AppSnackbar("Alarms Set no Watch")
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }
        }
    }

    fun sendAlarmsToPhone() {
        val executorService = Executors.newSingleThreadScheduledExecutor()
        val days = arrayListOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )

        val handler = Handler(Looper.getMainLooper()) // Create a handler to run on the main thread

        alarms.value.forEachIndexed { index, alarm ->
            if (alarm.enabled) {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Casio G-Shock Alarm")
                    putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                    putExtra(AlarmClock.EXTRA_DAYS, days)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Schedule the alarms with a one-second delay
                executorService.schedule({
                    api().preventReconnection()

                    // Use the handler to call startActivity on the main thread
                    handler.post {
                        applicationContext().startActivity(intent)
                    }
                }, index.toLong(), TimeUnit.SECONDS)
            }
        }
    }
}
