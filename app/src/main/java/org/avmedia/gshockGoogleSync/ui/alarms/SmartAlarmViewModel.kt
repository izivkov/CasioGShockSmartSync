package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockapi.model.SmartAlarm
import javax.inject.Inject

/** Reads and writes the DW-H5600 smart alarm (its one slot on the watch). */
@HiltViewModel
class SmartAlarmViewModel @Inject constructor(
    private val api: GShockRepository,
) : ViewModel() {

    data class State(
        val alarm: SmartAlarm? = null,
        val loaded: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { api.getH5600SmartAlarm() }
                .onSuccess { alarm -> _state.update { State(alarm = alarm, loaded = true) } }
                .onFailure { _state.update { State(loaded = true, error = "Could not read the smart alarm") } }
        }
    }

    fun save(alarm: SmartAlarm) {
        viewModelScope.launch {
            runCatching { api.setH5600SmartAlarm(alarm) }
                .onSuccess { _state.update { it.copy(alarm = alarm, error = null) } }
                .onFailure { _state.update { it.copy(error = "Could not write the smart alarm") } }
        }
    }
}
