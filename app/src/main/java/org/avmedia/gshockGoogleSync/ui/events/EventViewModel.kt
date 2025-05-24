package org.avmedia.gshockGoogleSync.ui.events

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.Utils
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
    val translateApi: TranslateRepository,
    private val calendarEvents: CalendarEvents,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    init {
        listenForUpdateRequest()
    }

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    fun loadEvents() {
        viewModelScope.launch {
            runCatching {
                val loadedEvents = calendarEvents.getEventsFromCalendar()
                _events.value = loadedEvents
                EventsModel.refresh(loadedEvents)
            }.onFailure {
                ProgressEvents.onNext("Arror", it.message)
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

    private fun String.sanitizeEventTitle(): String {
        fun String.filterAllowedCharacters(): String {
            val allowedSymbols =
                " !\"#\\\$%&'()*+,-./:;<=>?@[\\]^_`{|}" // Not supported on the watch: "~。「」、・。¥±♪⟪⟫♦▶◀"
            val regex = "[^A-Za-z0-9${Regex.escape(allowedSymbols)}]".toRegex()
            return this.replace(regex, "*")
        }

        fun String.removeEmojis(): String {
            return this.replace(Regex("[\\p{So}\\p{Cn}]"), "")
        }

        fun String.removeAccents(): String {
            val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
            return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized)
                .replaceAll("")
        }

        return this.removeEmojis().removeAccents().filterAllowedCharacters().trim()
    }

    fun sendEventsToWatch() {

        viewModelScope.launch {
            val result = runCatching {
                // Create a new list with emoji-free titles
                val sanitizedEvents = _events.value.map { event ->
                    event.copy(title = event.title.sanitizeEventTitle())
                }

                api.setEvents(ArrayList(sanitizedEvents))
                AppSnackbar(translateApi.getString(appContext, R.string.events_set))
            }

            result.onFailure { e ->
                ProgressEvents.onNext("Arror", e.message ?: "")
            }
        }
    }
}
