package org.avmedia.gshockGoogleSync.ui.health.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.avmedia.gshockGoogleSync.history.HistoryDb
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockGoogleSync.ui.health.HealthLogShare

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val dao = HistoryDb.get(context).dao()
    private val _ui = MutableStateFlow(WorkoutsUi())
    val ui: StateFlow<WorkoutsUi> = _ui

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.watchWorkouts().collect { list -> _ui.update { it.copy(workouts = list) } }
        }
    }

    val events = WorkoutEvents(
        onSelect = ::select,
        onExportCsv = { export(json = false) },
        onExportJson = { export(json = true) },
        onShareRaw = { HealthLogShare.shareWorkout(context) },
        onDownload = ::download,
    )

    private fun download() {
        if (_ui.value.downloading) return
        _ui.update { it.copy(downloading = true, downloadNote = "") }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { org.avmedia.gshockapi.io.H5600WorkoutIO.download(userAsked = true) }.getOrNull() }
            _ui.update { it.copy(downloading = false, downloadNote = WorkoutText.downloadNote(r?.downloaded, r?.error)) }
        }
    }

    private fun select(w: WorkoutRow?) {
        if (w == null) { _ui.update { it.copy(selected = null) }; return }
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { WorkoutDetail(w, dao.lapsOf(w.startMs, w.sport), dao.samplesOf(w.startMs, w.sport)) }
            _ui.update { it.copy(selected = d) }
        }
    }

    private fun export(json: Boolean) {
        viewModelScope.launch {
            val shared = withContext(Dispatchers.IO) { runCatching { WorkoutShare.share(context, json) }.getOrDefault(false) }
            _ui.update { it.copy(exportNote = if (shared) "" else "No workouts to export yet.") }
        }
    }
}
