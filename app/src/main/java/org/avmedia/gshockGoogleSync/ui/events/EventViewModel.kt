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
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.text.Normalizer
import java.util.regex.Pattern
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val api: GShockRepository,
    private val calendarEvents: CalendarEvents,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        listenForUpdateRequest()
    }

    fun loadEvents() {
        viewModelScope.launch {
            runCatching {
                val loadedEvents = calendarEvents.getEventsFromCalendar()
                _events.value = loadedEvents
                EventsModel.refresh(loadedEvents)
            }.onFailure {
                AppSnackbar("Error: ${it.message}")
            }
        }
    }

    fun toggleEvents(index: Int, isEnabled: Boolean) {
        _events.value = _events.value.toMutableList().apply {
            this[index] = this[index].copy(enabled = isEnabled)
        }
    }

    private fun listenForUpdateRequest() {
        val eventActions = arrayOf(
            EventAction("CalendarUpdated") {
                Timber.d("CalendarUpdated, events: ${EventsModel.events.size}")
                @Suppress("UNCHECKED_CAST")
                _events.value = ProgressEvents.getPayload("CalendarUpdated") as List<Event>
            },
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
