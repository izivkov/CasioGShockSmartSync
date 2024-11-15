package org.avmedia.gShockSmartSyncCompose.ui.events

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.api
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import org.avmedia.gShockSmartSyncCompose.utils.Utils
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class EventViewModel : ViewModel() {

    init {
        listenForUpdateRequest()
    }

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    fun loadEvents(context: Context) {
        viewModelScope.launch {
            try {
                val loadedEvents = CalendarEvents.getEventsFromCalendar(context)
                _events.value = loadedEvents
                EventsModel.refresh(context)
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError", e.message)
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
            try {
                val events = _events.value
                api().setEvents(ArrayList(_events.value))
                AppSnackbar("Events Set")
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }
        }
    }
}
