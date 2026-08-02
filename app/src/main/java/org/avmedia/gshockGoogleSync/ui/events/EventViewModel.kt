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
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.text.Normalizer
import java.util.regex.Pattern
import javax.inject.Inject

// ============================================================================
// Cyrillic -> Latin transliteration
// ============================================================================
// Watch displays are Latin-only with no diacritics, so this produces plain
// ASCII output rather than the accented Latin some transliteration standards
// (and general-purpose libraries) default to.
object CyrillicToLatin {

    private val table = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch",
        'ъ' to "",   // silent hard sign - correct Russian convention
        'ы' to "y",
        'ь' to "",   // silent soft sign
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private const val CYRILLIC_RANGE_START = 0x0400
    private const val CYRILLIC_RANGE_END = 0x04FF
    private fun isCyrillic(c: Char) = c.code in CYRILLIC_RANGE_START..CYRILLIC_RANGE_END

    fun transliterate(input: String): String =
        input.map { ch ->
            val lower = ch.lowercaseChar()
            if (isCyrillic(lower)) {
                val mapped = table[lower] ?: ch.toString()
                if (ch.isUpperCase()) mapped.replaceFirstChar { it.uppercase() } else mapped
            } else {
                ch.toString()
            }
        }.joinToString("")
}

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

        return this.removeEmojis()
            .let { CyrillicToLatin.transliterate(it) }
            .removeAccents()
            .filterAllowedCharacters()
            .trim()
    }

    fun sendEventsToWatch() {
        viewModelScope.launch {
            runCatching {
                // Create a new list with emoji-free, Latin-only titles
                val sanitizedEvents = _events.value.map { event ->
                    event.copy(title = event.title.sanitizeEventTitle())
                }

                api.setEvents(ArrayList(sanitizedEvents))
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
