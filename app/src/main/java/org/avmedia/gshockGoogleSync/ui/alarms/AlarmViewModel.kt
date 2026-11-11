package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.AlarmsWritten
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.model.Alarm
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for managing the Alarms screen.
 *
 * This ViewModel handles:
 * - Loading alarms from the watch via [GShockRepository].
 * - Loading and saving alarm names using [AlarmNameStorage].
 * - Maintaining the state of the alarms list.
 * - Sending updated alarms back to the watch (via [ActionContainer.SetAlarmAction]).
 * - Emitting one-time messages ([events]) for the screen to show as feedback.
 * - Syncing enabled alarms to the phone's native alarm app.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val api: GShockRepository,
    private val alarmNameStorage: AlarmNameStorage,
    private val watchFeatureManager: IWatchFeatureManager,
    private val actionContainer: ActionContainer,
    private val sessionManager: org.avmedia.gshockGoogleSync.session.SessionManager,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    // One-time messages for the screen to show as a snackbar. A SharedFlow fires once
    // per save, unlike a StateFlow, which would replay the last message on recomposition.
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var eventSubscription: AutoCloseable? = null

    init {
        loadAlarms()
        setupEventSubscription()
    }

    private fun setupEventSubscription() {
        eventSubscription = ProgressEvents.subscribe(
            arrayOf(
                EventAction("AlarmsUpdated") {
                    val payload = ProgressEvents.getPayload("AlarmsUpdated") as? AlarmsWritten
                    if (payload != null) {
                        _alarms.value = payload.alarms
                    } else {
                        loadAlarms()
                    }
                }
            )
        )
    }

    override fun onCleared() {
        eventSubscription?.close()
    }

    private fun loadAlarms() = viewModelScope.launch {
        fetchAndApplyAlarms()
    }

    private suspend fun fetchAndApplyAlarms() {
        runCatching {
            alarmNameStorage.load()

            val alarmsFromWatch = api.getAlarms()
                .take(watchFeatureManager.getAlarmCount())
                .mapIndexed { index, alarm ->
                    // Use AlarmNameStorage to get the name
                    val name = alarmNameStorage.get(index)
                    alarm.copy(name = name)
                }

            val newAlarms = if (watchFeatureManager.isFeatureSupported("alarms.chime")) {
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
            sessionManager.onError()
        }
    }

    private fun updateAlarm(index: Int, transform: (Alarm) -> Alarm) {
        _alarms.update { currentAlarms ->
            currentAlarms.mapIndexed { i, alarm ->
                if (i == index) transform(alarm) else alarm
            }
        }
        sessionManager.markPendingSave(org.avmedia.gshockGoogleSync.session.ComponentId.ALARMS)
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
     * Sends the current state of all alarms to the watch via
     * [ActionContainer.SetAlarmAction.runWithAlarms], under RunEnvironment.DIRECT_INVOCATION.
     *
     * This process involves:
     * 1. Normalizing any manually-edited (null-named) alarms to an empty name.
     * 2. Handing the exact list to SetAlarmAction, which persists names to
     *    AlarmNameStorage, writes alarms and the hourly chime setting when supported,
     *    reads back confirmed state, and emits "AlarmsUpdated".
     * 3. Emitting a result message on [events] for the screen to show.
     */
    fun sendAlarmsToWatch() {
        viewModelScope.launch {
            // Before sending, process the alarms to handle null names.
            val alarmsToSend = _alarms.value.map { alarm ->
                if (alarm.name == null) alarm.copy(name = "") else alarm
            }

            val setAlarmAction = actionContainer.getAction(ActionContainer.SetAlarmAction::class.java)
            if (!setAlarmAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                _events.emit(appContext.getString(R.string.alarm_send_failed))
                return@launch
            }

            val saved = setAlarmAction.runWithAlarms(
                appContext,
                alarmsToSend,
                updateHourlyChime = watchFeatureManager.isFeatureSupported("alarms.chime"),
            )

            if (saved) {
                sessionManager.clearPendingSave(org.avmedia.gshockGoogleSync.session.ComponentId.ALARMS)
            }

            _events.emit(
                if (saved) appContext.getString(R.string.alarms_set_to_watch)
                else appContext.getString(R.string.alarm_send_failed)
            )
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
                .forEach { (_, alarm) ->
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
                    delay(kotlin.time.Duration.parse("1s")) // Wait 1 second before processing the next one
                }
        }
    }
}