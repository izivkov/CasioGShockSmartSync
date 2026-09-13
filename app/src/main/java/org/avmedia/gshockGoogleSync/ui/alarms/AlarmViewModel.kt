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
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage
import org.avmedia.gshockGoogleSync.ui.actions.ActionContainer
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockGoogleSync.utils.subscribeToProgressEvents
import java.util.Calendar
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


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
 * - Sending updated alarms back to the watch (via [ActionContainer.SetAlarmAction]).
 * - Syncing enabled alarms to the phone's native alarm app.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val api: GShockRepository,
    private val alarmNameStorage: AlarmNameStorage,
    private val watchFeatureManager: IWatchFeatureManager,
    private val actionContainer: ActionContainer,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // ProgressEvents silently drops a second subscription registered under a
    // name it has already seen - which "AlarmViewModel" is, the moment this
    // ViewModel is ever recreated. A unique name per instance guarantees
    // this instance's subscription actually registers. See
    // subscribeToProgressEvents() for the full explanation.
    private var eventSubscriptionName: String? = null

    init {
        loadAlarms()
        setupEventSubscription()
    }

    private fun setupEventSubscription() {
        eventSubscriptionName = subscribeToProgressEvents("AlarmViewModel", arrayOf(
            org.avmedia.gshockapi.EventAction("AlarmsUpdated") {
                loadAlarms()
            }
        ))
    }

    override fun onCleared() {
        super.onCleared()
        eventSubscriptionName?.let { ProgressEvents.subscriber.stop(it) }
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
     * Sends the current state of all alarms to the watch via
     * [ActionContainer.SetAlarmAction.runWithAlarms], under RunEnvironment.DIRECT_INVOCATION.
     *
     * This process involves:
     * 1. Normalizing any manually-edited (null-named) alarms to an empty name.
     * 2. Handing the exact list to SetAlarmAction, which persists names to
     *    AlarmNameStorage, writes the list via [api.setAlarms], and emits
     *    "AlarmsUpdated" (which this ViewModel's own subscription uses to reload).
     * 3. Updating the hourly chime setting if applicable.
     */
    fun sendAlarmsToWatch() = viewModelScope.launch {
        // Before sending, process the alarms to handle null names.
        val alarmsToSend = _alarms.value.map { alarm ->
            if (alarm.name == null) alarm.copy(name = "") else alarm
        }

        val setAlarmAction = actionContainer.getAction(ActionContainer.SetAlarmAction::class.java)
        if (!setAlarmAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
            return@launch
        }

        setAlarmAction.runWithAlarms(appContext, alarmsToSend)

        if (watchFeatureManager.isFeatureSupported("alarms.chime")) {
            runCatching {
                val chimeSetting = alarmsToSend.getOrNull(0)?.hasHourlyChime ?: false
                api.setSettings(api.getSettings().copy(hourlyChime = chimeSetting))
            }.onFailure {
                ProgressEvents.onNext("Error", it.message ?: "")
            }
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
