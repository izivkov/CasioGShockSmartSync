package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import java.time.DayOfWeek
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class AlarmViewMode { SIMPLE, WEEKLY }

/**
 * Represents one-time UI events that should be handled by the UI layer.
 */
sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val api: GShockRepository,
    private val alarmNameStorage: AlarmNameStorage,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        const val ALARM_VIEW_MODE_KEY = "AlarmViewMode"
        const val ALARM_DAYS_KEY = "AlarmDaySelections"
    }

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _viewMode = MutableStateFlow(AlarmViewMode.SIMPLE)
    val viewMode: StateFlow<AlarmViewMode> = _viewMode.asStateFlow()

    private val _alarmDays = MutableStateFlow<Map<Int, Set<DayOfWeek>>>(emptyMap())
    val alarmDays: StateFlow<Map<Int, Set<DayOfWeek>>> = _alarmDays.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val modeStr = LocalDataStorage.get(appContext, ALARM_VIEW_MODE_KEY)
            _viewMode.value = if (modeStr == AlarmViewMode.WEEKLY.name) AlarmViewMode.WEEKLY else AlarmViewMode.SIMPLE

            val daysJson = LocalDataStorage.get(appContext, ALARM_DAYS_KEY)
            if (!daysJson.isNullOrBlank()) {
                _alarmDays.value = parseDaysJson(daysJson)
            }
        }
        loadAlarms()
    }

    private fun loadAlarms() = viewModelScope.launch {
        runCatching {
            alarmNameStorage.load()

            val alarmsFromWatch = api.getAlarms()
                .take(WatchInfo.alarmCount)
                .mapIndexed { index, alarm ->
                    val name = alarmNameStorage.get(index)
                    alarm.copy(name = name)
                }

            val newAlarms = if (WatchInfo.chimeInSettings) {
                val settings = api.getSettings()
                alarmsFromWatch.mapIndexed { index, alarm ->
                    if (index == 0) alarm.copy(hasHourlyChime = settings.hourlyChime)
                    else alarm
                }
            } else {
                alarmsFromWatch
            }
            _alarms.value = newAlarms
            ProgressEvents.onNext("Alarms Loaded")
        }.onFailure {
            ProgressEvents.onNext("Error")
        }
    }

    private fun updateAlarm(index: Int, transform: (Alarm) -> Alarm) {
        _alarms.update { currentAlarms ->
            currentAlarms.mapIndexed { i, alarm ->
                if (i == index) transform(alarm) else alarm
            }
        }
    }

    fun toggleAlarm(index: Int, isEnabled: Boolean) =
        updateAlarm(index) { it.copy(enabled = isEnabled) }

    fun onTimeChanged(index: Int, hours: Int, minutes: Int) {
        updateAlarm(index) { it.copy(hour = hours, minute = minutes, name = null) }
    }

    fun toggleHourlyChime(enabled: Boolean) =
        updateAlarm(0) { it.copy(hasHourlyChime = enabled) }

    fun setViewMode(mode: AlarmViewMode) {
        _viewMode.value = mode
        LocalDataStorage.put(appContext, ALARM_VIEW_MODE_KEY, mode.name)
    }

    fun toggleDay(alarmIndex: Int, day: DayOfWeek) {
        _alarmDays.update { current ->
            val days = current[alarmIndex] ?: emptySet()
            val updated = if (day in days) days - day else days + day
            current + (alarmIndex to updated)
        }
        saveDays()
    }

    private fun saveDays() {
        val toStore = _alarmDays.value
            .mapKeys { it.key.toString() }
            .mapValues { it.value.map { d -> d.name } }
        LocalDataStorage.put(appContext, ALARM_DAYS_KEY, Gson().toJson(toStore))
    }

    private fun parseDaysJson(json: String): Map<Int, Set<DayOfWeek>> {
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val raw = runCatching {
            Gson().fromJson<Map<String, List<String>>>(json, type)
        }.getOrNull() ?: return emptyMap()
        return raw.mapNotNull { (k, v) ->
            val index = k.toIntOrNull() ?: return@mapNotNull null
            val days = v.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
            index to days
        }.toMap()
    }

    fun sendAlarmsToWatch() = viewModelScope.launch {
        val alarmsToSend = _alarms.value.mapIndexed { index, alarm ->
            if (alarm.name == null) {
                alarmNameStorage.put("", index)
                alarm.copy(name = "")
            } else {
                alarm
            }
        }

        alarmNameStorage.save()

        runCatching {
            api.setAlarms(ArrayList(alarmsToSend))
            if (WatchInfo.chimeInSettings) {
                val chimeSetting = alarmsToSend.getOrNull(0)?.hasHourlyChime ?: false
                api.setSettings(api.getSettings().copy(hourlyChime = chimeSetting))
            }

            loadAlarms()

            AppSnackbar(appContext.getString(R.string.alarms_set_to_watch))
        }.onFailure {
            ProgressEvents.onNext("Error", it.message ?: "")
        }
    }

    fun sendAlarmsToPhone() {
        val allDays = arrayListOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        viewModelScope.launch {
            _alarms.value
                .withIndex()
                .filter { it.value.enabled }
                .forEach { (index, alarm) ->
                    val selectedDays = _alarmDays.value[index]
                    val days = if (selectedDays.isNullOrEmpty()) allDays
                               else ArrayList(selectedDays.map { it.toCalendarDay() })

                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_MESSAGE, alarm.name)
                        putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                        putExtra(AlarmClock.EXTRA_DAYS, days)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    api.preventReconnection()
                    appContext.startActivity(intent)
                    delay(1000L)
                }
        }
    }

    private fun DayOfWeek.toCalendarDay(): Int = when (this) {
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
    }
}
