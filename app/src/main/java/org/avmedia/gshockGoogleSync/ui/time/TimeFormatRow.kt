package org.avmedia.gshockGoogleSync.ui.time

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppChoiceButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import javax.inject.Inject

/** Watch 12h / 24h display, moved here from Settings; applied immediately. */
@HiltViewModel
class TimeFormatViewModel @Inject constructor(
    private val api: GShockRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    data class State(val is24h: Boolean? = null, val busy: Boolean = false)

    private val _state = MutableStateFlow(State(is24h = org.avmedia.gshockGoogleSync.utils.LastKnown.bool(context, "time_24h")))
    val state = _state.asStateFlow()

    /** Reads the watch's format; called each time the Time tab is shown (the watch may connect later). */
    fun refresh() {
        viewModelScope.launch {
            repeat(3) { attempt ->
                val v = runCatching { withTimeoutOrNull(6_000) { api.getTimeFormat24h() } }.getOrNull()
                if (v != null) {
                    _state.value = State(is24h = v)
                    org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "time_24h", v)
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.set(context, v) // the whole app follows the watch
                    return@launch
                }
                kotlinx.coroutines.delay(3_000L * (attempt + 1))
            }
        }
    }

    fun set(is24h: Boolean) {
        viewModelScope.launch {
            _state.value = State(is24h = is24h, busy = true) // move the UI immediately
            runCatching {
                kotlinx.coroutines.withTimeout(20_000) { api.setTimeFormat24h(is24h) }
                kotlinx.coroutines.withTimeoutOrNull(10_000) { api.getTimeFormat24h() }
            }
                .onSuccess {
                    _state.value = State(is24h = it)
                    it?.let { v ->
                        org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "time_24h", v)
                        org.avmedia.gshockGoogleSync.utils.AppTimeFormat.set(context, v)
                    }
                }
                .onFailure {
                    _state.value = State(is24h = !is24h)
                    AppSnackbar("Time format: ${it.message}")
                }
        }
    }
}

@Composable
fun TimeFormatRow(modifier: Modifier = Modifier, vm: TimeFormatViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }
    AppCard(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(text = "Watch time format")
            if (s.is24h == null) {
                org.avmedia.gshockGoogleSync.ui.common.LoadingSpinner() // never read from the watch
            } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Current format highlighted; greyed only while syncing.
                AppChoiceButton(text = "12h", selected = s.is24h == false, enabled = !s.busy, onClick = { vm.set(false) })
                AppChoiceButton(text = "24h", selected = s.is24h == true, enabled = !s.busy, onClick = { vm.set(true) })
            }
        }
    }
}
