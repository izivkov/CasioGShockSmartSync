package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val api: GShockRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private var _alarms by mutableStateOf<List<Alarm>>(emptyList())
    val alarms: List<Alarm> get() = _alarms

    init {
        loadAlarms()
    }

    private fun loadAlarms() = viewModelScope.launch {
        runCatching {
            _alarms = api.getAlarms()
                .take(WatchInfo.alarmCount)
                .let { alarms ->
                    if (WatchInfo.chimeInSettings) {
                        val settings = api.getSettings()
                        alarms.mapIndexed { index, alarm ->
                            if (index == 0) alarm.copy(hasHourlyChime = settings.hourlyChime)
                            else alarm
                        }
                    } else alarms
                }
            ProgressEvents.onNext("Alarms Loaded")
        }.onFailure {
            ProgressEvents.onNext("Error")
        }
    }

    private fun updateAlarm(index: Int, transform: (Alarm) -> Alarm) {
        _alarms = _alarms.mapIndexed { i, alarm ->
            if (i == index) transform(alarm) else alarm
        }
    }

    fun toggleAlarm(index: Int, isEnabled: Boolean) =
        updateAlarm(index) { it.copy(enabled = isEnabled) }

    fun onTimeChanged(index: Int, hours: Int, minutes: Int) =
        updateAlarm(index) { it.copy(hour = hours, minute = minutes) }

    fun toggleHourlyChime(enabled: Boolean) =
        updateAlarm(0) { it.copy(hasHourlyChime = enabled) }

    fun sendAlarmsToWatch() = viewModelScope.launch {
        runCatching {
            api.setAlarms(ArrayList(alarms))
            if (WatchInfo.chimeInSettings) {
                api.setSettings(api.getSettings().copy(hourlyChime = alarms[0].hasHourlyChime))
            }
            AppSnackbar(appContext.getString(R.string.alarms_set_to_watch))
        }.onFailure {
            ProgressEvents.onNext("Error", it.message ?: "")
        }
    }

    fun sendAlarmsToPhone() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val handler = Handler(Looper.getMainLooper())
        val days = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        alarms
            .withIndex()
            .filter { it.value.enabled }
            .forEach { (index, alarm) ->
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Casio G-Shock Alarm")
                    putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                executor.schedule({
                    api.preventReconnection()
                    handler.post { appContext.startActivity(intent) }
                }, index.toLong(), TimeUnit.SECONDS)
            }
    }
}
