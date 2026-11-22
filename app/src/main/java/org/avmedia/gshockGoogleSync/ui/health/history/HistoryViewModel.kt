package org.avmedia.gshockGoogleSync.ui.health.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.avmedia.gshockGoogleSync.history.HistoryDb

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val dao = HistoryDb.get(context).dao()
    private val _ui = MutableStateFlow(HistoryUi())
    val ui: StateFlow<HistoryUi> = _ui

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(dao.watchDays(), dao.watchNights()) { d, n -> d to n }.collect { (d, n) ->
                _ui.update { it.copy(days = d, nights = n, today = LocalDate.now()) }
            }
        }
    }

    val events = HistoryEvents(
        onRange = { r -> _ui.update { it.copy(range = r) } },
        onMonth = { delta -> _ui.update { it.copy(month = it.month.plusMonths(delta.toLong()), selected = null) } },
        onSelect = ::select,
        onExportCsv = { export(json = false) },
        onExportJson = { export(json = true) },
    )

    private fun select(date: LocalDate?) {
        _ui.update { it.copy(selected = date, selectedHours = emptyList()) }
        if (date == null) return
        viewModelScope.launch {
            val hours = withContext(Dispatchers.IO) { dao.hoursOf(date.toString()) }
            _ui.update { if (it.selected == date) it.copy(selectedHours = hours) else it }
        }
    }

    private fun export(json: Boolean) {
        viewModelScope.launch {
            val shared = withContext(Dispatchers.IO) { runCatching { HistoryShare.share(context, json) }.getOrDefault(false) }
            _ui.update { it.copy(exportNote = if (shared) "" else "Nothing to export yet.") }
        }
    }
}
