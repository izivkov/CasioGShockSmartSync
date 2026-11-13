package org.avmedia.gshockGoogleSync.ui.events

import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.EventStorage
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.session.ComponentId
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.RepeatPeriod
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val api: GShockRepository,
    private val calendarEvents: CalendarEvents,
    private val eventStorage: EventStorage,
    private val actionContainer: ActionContainer,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    /** True while the reminders were changed in the app and not yet sent to the watch. */
    val isModified: StateFlow<Boolean> = sessionManager.isPendingSaveFlow(ComponentId.EVENTS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.isPendingSave(ComponentId.EVENTS))

    private val _isManualMode = MutableStateFlow(eventStorage.isManualMode())
    val isManualMode: StateFlow<Boolean> = _isManualMode

    private var loadEventsJob: Job? = null

    init {
        refreshState()
        listenForUpdateRequest()
        refreshOnSetupComplete()
    }

    /** Reloads when a connection finishes setup after this ViewModel was created. */
    private fun refreshOnSetupComplete() {
        viewModelScope.launch {
            sessionManager.sessionState
                .map { it.isSetupComplete }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect {
                    if (!_isManualMode.value) // We are refreshing on new Calendar Events only, not in Manual mode
                        refreshState()
                }
        }
    }

    private fun refreshState() {
        viewModelScope.launch {
            runCatching {
                eventStorage.load()
                _isManualMode.value = eventStorage.isManualMode()
                loadEvents()
            }.onFailure {
                Timber.e(it, "Failed to refresh state")
            }
        }
    }

    /** Re-reads the events, unless the user has edits that are not yet sent to the watch. */
    fun loadEventsUnlessModified() {
        if (!sessionManager.isPendingSave(ComponentId.EVENTS)) loadEvents()
    }

    fun loadEvents() {
        loadEventsJob?.cancel()
        loadEventsJob = viewModelScope.launch {
            runCatching {
                if (_isManualMode.value) {
                    val loadedEvents = api.getEventsFromWatch()
                        .take(EventsModel.MAX_REMINDERS)
                        .toMutableList()

                    while (loadedEvents.size < EventsModel.MAX_REMINDERS) {
                        loadedEvents.add(
                            Event(
                                "",
                                EventsModel.createEventDate(
                                    System.currentTimeMillis(),
                                    java.time.ZoneId.systemDefault()
                                ),
                                null,
                                repeatPeriod = RepeatPeriod.NEVER,
                                daysOfWeek = null,
                                enabled = false,
                                incompatible = false
                            )
                        )
                    }
                    _events.value = loadedEvents
                    EventsModel.refresh(ArrayList(loadedEvents))
                    sessionManager.clearPendingSave(ComponentId.EVENTS)
                } else {
                    val loadedEvents = calendarEvents.getEventsFromCalendar()
                        .take(EventsModel.MAX_REMINDERS)
                    _events.value = loadedEvents
                    EventsModel.refresh(ArrayList(loadedEvents))
                    sessionManager.clearPendingSave(ComponentId.EVENTS)
                }
            }.onFailure {
                if (it !is kotlinx.coroutines.CancellationException) {
                    AppSnackbar("Error: ${it.message}")
                }
            }
        }
    }

    fun toggleManualMode(enabled: Boolean) {
        viewModelScope.launch {
            _isManualMode.value = enabled
            eventStorage.setManualMode(enabled)
            eventStorage.save()
            loadEvents()
        }
    }

    fun updateEvent(index: Int, event: Event) {
        _events.value = _events.value.toMutableList().apply {
            this[index] = event
        }
        EventsModel.refresh(ArrayList(_events.value))
        sessionManager.markPendingSave(ComponentId.EVENTS)
    }

    fun toggleEvents(index: Int, isEnabled: Boolean) {
        _events.value = _events.value.toMutableList().apply {
            this[index] = this[index].copy(enabled = isEnabled)
        }
        EventsModel.refresh(ArrayList(_events.value))
        sessionManager.markPendingSave(ComponentId.EVENTS)
    }

    private var eventSubscription: AutoCloseable? = null

    override fun onCleared() {
        eventSubscription?.close()
    }

    private fun listenForUpdateRequest() {
        val eventActions = arrayOf(
            EventAction("CalendarUpdated") {
                if (!_isManualMode.value) {
                    Timber.d("CalendarUpdated, events: ${EventsModel.events.size}")
                    @Suppress("UNCHECKED_CAST")
                    val newEvents = ProgressEvents.getPayload("CalendarUpdated") as List<Event>
                    _events.value = newEvents
                    EventsModel.refresh(ArrayList(newEvents))
                    sessionManager.clearPendingSave(ComponentId.EVENTS)
                }
            },
            EventAction("EventsUpdated") {
                @Suppress("UNCHECKED_CAST")
                val payload = ProgressEvents.getPayload("EventsUpdated") as? List<Event>
                if (_isManualMode.value && payload != null) {
                    _events.value = payload
                    EventsModel.refresh(ArrayList(payload))
                    sessionManager.clearPendingSave(ComponentId.EVENTS)
                } else {
                    loadEvents()
                }
            }
        )

        eventSubscription = ProgressEvents.subscribe(eventActions)
    }

    fun sendEventsToWatch() {
        viewModelScope.launch {
            val setEventsAction =
                actionContainer.getAction(ActionContainer.SetEventsAction::class.java)
            if (!setEventsAction.shouldRun(ActionContainer.RunEnvironment.DIRECT_INVOCATION)) {
                return@launch
            }
            // Disable the Send button right away; it is re-enabled below only if the send fails.
            sessionManager.clearPendingSave(ComponentId.EVENTS)
            if (!setEventsAction.runWithEvents(appContext, _events.value)) {
                sessionManager.markPendingSave(ComponentId.EVENTS)
            }
        }
    }
}