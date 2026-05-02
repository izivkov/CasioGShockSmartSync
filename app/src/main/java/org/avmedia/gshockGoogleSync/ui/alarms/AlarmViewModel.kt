package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.DayOfWeek
import java.time.LocalDateTime
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
        private const val DEFAULT_LOCAL_ALARM_COUNT = 5
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
            _viewMode.value = AlarmSyncStorage.loadViewMode(appContext)
            _alarmDays.value = AlarmSyncStorage.loadDaySelections(appContext)

            AlarmSyncStorage.loadAlarms(appContext)?.let {
                _alarms.value = it
            }
        }
        loadAlarms()
    }

    private fun loadAlarms() = viewModelScope.launch {
        runCatching {
            val localDraft = AlarmSyncStorage.loadAlarms(appContext)
            if (!api.isConnected()) {
                _alarms.value = localDraft ?: createDefaultLocalAlarms()
                return@runCatching
            }

            if (AlarmSyncStorage.isDirty(appContext) && !localDraft.isNullOrEmpty()) {
                _alarms.value = localDraft
                return@runCatching
            }

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
            AlarmSyncStorage.saveAlarms(appContext, newAlarms, dirty = false)
            ProgressEvents.onNext("Alarms Loaded")
        }.onFailure {
            _alarms.value = AlarmSyncStorage.loadAlarms(appContext)
                ?: createDefaultLocalAlarms()
            ProgressEvents.onNext("Error")
        }
    }

    private fun createDefaultLocalAlarms(): List<Alarm> {
        val count = WatchInfo.alarmCount.takeIf { it > 0 } ?: DEFAULT_LOCAL_ALARM_COUNT
        return List(count) { Alarm(0, 0, false, false, "") }
    }

    private fun updateAlarm(index: Int, transform: (Alarm) -> Alarm) {
        val updated = _alarms.value.mapIndexed { i, alarm ->
            if (i == index) transform(alarm) else alarm
        }
        _alarms.value = updated
        AlarmSyncStorage.saveAlarms(appContext, updated, dirty = true)
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
        AlarmSyncStorage.saveViewMode(appContext, mode)
        AlarmSyncStorage.saveAlarms(appContext, _alarms.value, dirty = true)
    }

    fun toggleDay(alarmIndex: Int, day: DayOfWeek) {
        _alarmDays.update { current ->
            val days = current[alarmIndex] ?: emptySet()
            val updated = if (day in days) days - day else days + day
            current + (alarmIndex to updated)
        }
        saveDays()
        AlarmSyncStorage.saveAlarms(appContext, _alarms.value, dirty = true)
    }

    private fun saveDays() {
        AlarmSyncStorage.saveDaySelections(appContext, _alarmDays.value)
    }

    fun sendAlarmsToWatch() = viewModelScope.launch {
        if (!api.isConnected()) {
            AppSnackbar(appContext.getString(R.string.watch_not_connected))
            return@launch
        }

        val desiredAlarms = _alarms.value.mapIndexed { index, alarm ->
            if (alarm.name == null) {
                alarmNameStorage.put("", index)
                alarm.copy(name = "")
            } else {
                alarm
            }
        }

        alarmNameStorage.save()
        AlarmSyncStorage.saveAlarms(appContext, desiredAlarms, dirty = true)

        val alarmsToSend = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = desiredAlarms,
            alarmDays = _alarmDays.value,
            now = LocalDateTime.now(),
            viewMode = _viewMode.value
        )

        runCatching {
            api.getAlarms()
            api.setAlarms(ArrayList(alarmsToSend))
            if (WatchInfo.chimeInSettings) {
                val chimeSetting = alarmsToSend.getOrNull(0)?.hasHourlyChime ?: false
                api.setSettings(api.getSettings().copy(hourlyChime = chimeSetting))
            }

            AlarmSyncStorage.saveAlarms(appContext, desiredAlarms, dirty = false)

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
