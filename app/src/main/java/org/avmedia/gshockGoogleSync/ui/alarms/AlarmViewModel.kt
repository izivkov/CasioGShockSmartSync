package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage // Import the new class
import org.avmedia.gshockGoogleSync.scratchpad.ScratchpadManager
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
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


/**
 * Represents one-time UI events that should be handled by the UI layer.
 */
sealed class UiEvent {
    /**
     * Event to show a Snackbar with a specific message.
     * @property message The text message to display.
     */
    data class ShowSnackbar(val message: String) : UiEvent()
}

/**
 * ViewModel for managing the Alarms screen.
 *
 * This ViewModel handles:
 * - Loading alarms from the watch via [GShockRepository].
 * - loading and saving alarm names using [AlarmNameStorage].
 * - Maintaining the state of the alarms list.
 * - Sending updated alarms back to the watch.
 * - Syncing enabled alarms to the phone's native alarm app.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val api: GShockRepository,
    private val alarmNameStorage: AlarmNameStorage,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        loadAlarms()
    }

    private fun loadAlarms() = viewModelScope.launch {
        runCatching {
            alarmNameStorage.load()

            val alarmsFromWatch = api.getAlarms()
                .take(WatchInfo.alarmCount)
                .mapIndexed { index, alarm ->
                    // Use AlarmNameStorage to get the name
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

    /**
     * Toggles the enabled state of an alarm at the specified index.
     *
     * @param index The index of the alarm in the list.
     * @param isEnabled The new enabled state.
     */
    fun toggleAlarm(index: Int, isEnabled: Boolean) =
        updateAlarm(index) { it.copy(enabled = isEnabled) }

    /**
     * Updates the time for a specific alarm.
     *
     * Note: When the time is changed, the alarm name is set to null to indicate
     * that it has been manually edited and may need its name cleared or updated.
     *
     * @param index The index of the alarm.
     * @param hours The new hour (0-23).
     * @param minutes The new minute (0-59).
     */
    fun onTimeChanged(index: Int, hours: Int, minutes: Int) {
        // When the time is changed, the UI state is updated, setting the name to null
        // to signify it has been manually edited.
        updateAlarm(index) { it.copy(hour = hours, minute = minutes, name = null) }
    }

    /**
     * Toggles the hourly chime setting (Hourly Signal) for the watch.
     * This is typically associated with the first alarm slot on some models.
     *
     * @param enabled The new state of the hourly chime.
     */
    fun toggleHourlyChime(enabled: Boolean) =
        updateAlarm(0) { it.copy(hasHourlyChime = enabled) }

    /**
     * Sends the current state of all alarms to the watch.
     *
     * This process involves:
     * 1. Updating the `AlarmNameStorage` with any name changes (clearing names for edited alarms).
     * 2. Sending the list of alarms to the watch via [api.setAlarms].
     * 3. Updating the hourly chime setting if applicable.
     * 4. Reloading the alarms from the watch to confirm the state.
     * 5. Emitting a [UiEvent.ShowSnackbar] on success.
     */
    fun sendAlarmsToWatch() = viewModelScope.launch {
        // Before sending, process the alarms to handle null names.
        val alarmsToSend = _alarms.value.mapIndexed { index, alarm ->
            if (alarm.name == null) {
                // This alarm was manually edited. Update its name in storage to be empty.
                // Using an empty string with `put` will store the NO_NAME_INDEX for that slot.
                alarmNameStorage.put("", index)

                // Return a clean alarm object to be sent to the watch API.
                alarm.copy(name = "")
            } else {
                alarm
            }
        }

        // Save any changes made in the loop above to the watch's scratchpad.
        alarmNameStorage.save()

        runCatching {
            api.setAlarms(ArrayList(alarmsToSend))
            if (WatchInfo.chimeInSettings) {
                // Ensure we get the latest hourly chime setting from the potentially modified list
                val chimeSetting = alarmsToSend.getOrNull(0)?.hasHourlyChime ?: false
                api.setSettings(api.getSettings().copy(hourlyChime = chimeSetting))
            }

            // After successfully sending, reload the alarms state from the watch to ensure UI consistency.
            loadAlarms() // Reload state from the watch after saving.

            _uiEvents.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.alarms_set_to_watch)))
        }.onFailure {
            ProgressEvents.onNext("Error", it.message ?: "")
        }
    }


    /**
     * Sends the enabled alarms from the app to the phone's native Alarm Clock application.
     *
     * This creates an alarm intent for each enabled alarm in the list and starts it.
     * It includes a delay between intents to ensure they are processed correctly.
     */
    fun sendAlarmsToPhone() {
        val days = arrayListOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        viewModelScope.launch {
            _alarms.value
                .withIndex()
                .filter { it.value.enabled }
                .forEach { (index, alarm) ->
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
                    delay(1000L) // Wait 1 second before processing the next one
                }
        }
    }
}
