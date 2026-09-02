package org.avmedia.gshockGoogleSync.ui.events

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.CyrillicToLatin
import org.avmedia.gshockGoogleSync.scratchpad.EventStorage
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.EventDate
import org.avmedia.gshockapi.model.RepeatPeriod
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import kotlinx.coroutines.Job
import java.text.Normalizer
import java.util.regex.Pattern
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val api: GShockRepository,
    private val calendarEvents: CalendarEvents,
    private val eventStorage: EventStorage,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    private val _isManualMode = MutableStateFlow(eventStorage.isManualMode())
    val isManualMode: StateFlow<Boolean> = _isManualMode

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private var loadEventsJob: Job? = null

    init {
        refreshState()
        listenForUpdateRequest()
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
                                RepeatPeriod.NEVER,
                                null,
                                false,
                                false
                            )
                        )
                    }
                    _events.value = loadedEvents
                    EventsModel.refresh(ArrayList(loadedEvents))
                } else {
                    val loadedEvents = calendarEvents.getEventsFromCalendar()
                        .take(EventsModel.MAX_REMINDERS)
                    _events.value = loadedEvents
                    EventsModel.refresh(ArrayList(loadedEvents))
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
    }

    fun toggleEvents(index: Int, isEnabled: Boolean) {
        _events.value = _events.value.toMutableList().apply {
            this[index] = this[index].copy(enabled = isEnabled)
        }
        EventsModel.refresh(ArrayList(_events.value))
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
                }
            },
            EventAction("DeviceName") {
                if (!_isManualMode.value) // We are refreshing on new Calendar Events only, not in Manual mode
                    refreshState()
            },
            EventAction("ConnectionSetupComplete") {
                if (!_isManualMode.value) // We are refreshing on new Calendar Events only, not in Manual mode
                    refreshState()
            }
        )

        ProgressEvents.runEventActions(
            Utils.AppHashCode() + "listenForUpdateRequest",
            eventActions
        )
    }

    fun sendEventsToWatch() {
        viewModelScope.launch {
            runCatching {
                val eventTransformers: List<(List<Event>) -> List<Event>> = listOf(
                    { events ->
                        events.map { event ->
                            event.copy(title = CyrillicToLatin.transliterate(event.title))
                        }
                    }
                )

                val processedEvents = eventTransformers.fold(_events.value) { currentEvents, transformer ->
                    transformer(currentEvents)
                }

                api.setEvents(ArrayList(processedEvents))
                AppSnackbar(appContext.getString(R.string.reminders_sent_to_watch))
            }.onFailure { e ->
                AppSnackbar("Error: ${e.message ?: ""}")
            }
        }
    }

    /**
     * Represents one-time UI events that should be handled by the UI layer.
     */
    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
    }
}
