package com.beamburst.casswatch.ui.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.beamburst.casswatch.R
import com.beamburst.casswatch.data.repository.GShockRepository
import com.beamburst.casswatch.scratchpad.AlarmNameStorage
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import com.beamburst.casswatch.ui.common.AppSnackbar
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
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
    object RequestExactAlarmPermission : UiEvent()
}

data class AlarmDraft(
    val index: Int,
    val hour: Int,
    val minute: Int,
    val name: String,
    val days: Set<DayOfWeek>,
    val viewMode: AlarmViewMode
)

fun alarmHash(
    hour: Int,
    minute: Int,
    days: Set<DayOfWeek>,
    enabled: Boolean
): String {
    val dayMask = if (days.isEmpty()) "ALL" else days.sorted().joinToString(",") { it.name }
    return "$hour:$minute:$dayMask:$enabled"
}

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val api: GShockRepository,
    private val alarmNameStorage: AlarmNameStorage,
    private val alarmSyncState: AlarmSyncState,
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

    private val _firedAts = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val firedAts: StateFlow<Map<Int, Long>> = _firedAts.asStateFlow()

    private val _editorTarget = MutableStateFlow<AlarmDraft?>(null)
    val editorTarget: StateFlow<AlarmDraft?> = _editorTarget.asStateFlow()

    private var exactAlarmPermissionRequested = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _viewMode.value = AlarmSyncStorage.loadViewMode(appContext)
            _alarmDays.value = AlarmSyncStorage.loadDaySelections(appContext)
            _firedAts.value = AlarmSyncStorage.loadFiredAts(appContext)

            AlarmSyncStorage.loadAlarms(appContext)?.let {
                _alarms.value = it
            }
        }
        loadAlarms()
        startFireTimeTick()
    }

    private fun startFireTimeTick() = viewModelScope.launch {
        while (true) {
            delay(60_000)
            tickFireOnceAlarms()
        }
    }

    private fun tickFireOnceAlarms() {
        if (_viewMode.value != AlarmViewMode.WEEKLY) return
        val now = System.currentTimeMillis()
        val nowTime = LocalTime.now()
        var anyFired = false

        val updatedFiredAts = _firedAts.value.toMutableMap()
        _alarms.value.forEachIndexed { index, alarm ->
            val isFireOnce = alarm.enabled &&
                _alarmDays.value[index].isNullOrEmpty() &&
                !updatedFiredAts.containsKey(index)
            if (isFireOnce && nowTime.isAfter(LocalTime.of(alarm.hour, alarm.minute))) {
                updatedFiredAts[index] = now
                anyFired = true
            }
        }

        if (anyFired) {
            _firedAts.value = updatedFiredAts
            AlarmSyncStorage.saveAlarms(appContext, _alarms.value, dirty = true, firedAts = updatedFiredAts)
        }
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
        AlarmSyncStorage.saveAlarms(appContext, updated, dirty = true, firedAts = _firedAts.value)
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
        AlarmSyncStorage.saveAlarms(appContext, _alarms.value, dirty = true, firedAts = _firedAts.value)
    }

    fun toggleDay(alarmIndex: Int, day: DayOfWeek) {
        _alarmDays.update { current ->
            val days = current[alarmIndex] ?: emptySet()
            val updated = if (day in days) days - day else days + day
            current + (alarmIndex to updated)
        }
        saveDays()
        AlarmSyncStorage.saveAlarms(appContext, _alarms.value, dirty = true, firedAts = _firedAts.value)
    }

    private fun saveDays() {
        AlarmSyncStorage.saveDaySelections(appContext, _alarmDays.value)
    }

    fun sendAlarmsToWatch() = viewModelScope.launch {
        if (!api.isConnected()) {
            AppSnackbar(appContext.getString(R.string.watch_not_connected))
            return@launch
        }

        val currentFiredAts = _firedAts.value

        val desiredAlarms = _alarms.value.mapIndexed { index, alarm ->
            if (alarm.name == null) {
                alarmNameStorage.put("", index)
                alarm.copy(name = "")
            } else {
                alarm
            }
        }

        alarmNameStorage.save()
        AlarmSyncStorage.saveAlarms(appContext, desiredAlarms, dirty = true, firedAts = currentFiredAts)

        val alarmsToSend = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = desiredAlarms,
            alarmDays = _alarmDays.value,
            now = LocalDateTime.now(),
            viewMode = _viewMode.value,
            firedAts = currentFiredAts
        )

        runCatching {
            api.getAlarms()
            api.setAlarms(ArrayList(alarmsToSend))
            if (WatchInfo.chimeInSettings) {
                val chimeSetting = alarmsToSend.getOrNull(0)?.hasHourlyChime ?: false
                api.setSettings(api.getSettings().copy(hourlyChime = chimeSetting))
            }

            val hashes = desiredAlarms.mapIndexed { index, alarm ->
                val days = _alarmDays.value[index] ?: emptySet()
                alarmHash(alarm.hour, alarm.minute, days, alarm.enabled)
            }
            alarmSyncState.update(
                AlarmSyncStorage.SyncRecord(
                    syncedAt = System.currentTimeMillis(),
                    sentAlarmHashes = hashes
                )
            )

            val updatedFiredAts = currentFiredAts.toMutableMap()
            desiredAlarms.forEachIndexed { index, _ ->
                if (currentFiredAts.containsKey(index) && !alarmsToSend[index].enabled) {
                    updatedFiredAts.remove(index)
                    updateAlarm(index) { it.copy(enabled = false) }
                }
            }
            _firedAts.value = updatedFiredAts
            AlarmSyncStorage.saveAlarms(appContext, _alarms.value, dirty = false, firedAts = updatedFiredAts)

            AppSnackbar(appContext.getString(R.string.alarms_set_to_watch))
        }.onFailure {
            ProgressEvents.onNext("Error", it.message ?: "")
        }
    }

    fun openEditor(index: Int) {
        val alarm = _alarms.value.getOrNull(index) ?: return
        _editorTarget.value = AlarmDraft(
            index = index,
            hour = alarm.hour,
            minute = alarm.minute,
            name = alarm.name ?: "",
            days = _alarmDays.value[index] ?: emptySet(),
            viewMode = _viewMode.value
        )
    }

    fun dismissEditor() {
        _editorTarget.value = null
    }

    fun upsertAlarm(index: Int, hour: Int, minute: Int, name: String, days: Set<DayOfWeek>) {
        val isFireOnce = _viewMode.value == AlarmViewMode.WEEKLY && days.isEmpty()
        if (isFireOnce && !exactAlarmPermissionRequested) {
            exactAlarmPermissionRequested = true
            viewModelScope.launch { _uiEvents.emit(UiEvent.RequestExactAlarmPermission) }
        }

        val existing = _alarms.value.getOrNull(index)
        val timeChanged = existing?.hour != hour || existing.minute != minute
        if (timeChanged) {
            val updated = _firedAts.value.toMutableMap().also { it.remove(index) }
            _firedAts.value = updated
        }

        updateAlarm(index) { it.copy(hour = hour, minute = minute, name = name.ifBlank { null }) }
        _alarmDays.update { it + (index to days) }
        AlarmSyncStorage.saveDaySelections(appContext, _alarmDays.value)
        _editorTarget.value = null
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
