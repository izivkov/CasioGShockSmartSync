package org.avmedia.gshockGoogleSync.ui.time

import AppSwitch
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
    data class State(val is24h: Boolean? = null, val busy: Boolean = false, val hourlyChime: Boolean? = null)

    private val _state = MutableStateFlow(
        State(is24h = org.avmedia.gshockGoogleSync.utils.LastKnown.bool(context, "time_24h"), hourlyChime = org.avmedia.gshockGoogleSync.utils.LastKnown.bool(context, "hourly_chime")),
    )
    val state = _state.asStateFlow()

    /** Reads the watch's format; called each time the Time tab is shown (the watch may connect later). */
    fun refresh() {
        viewModelScope.launch {
            repeat(3) { attempt ->
                val v = runCatching { withTimeoutOrNull(6_000) { api.getTimeFormat24h() } }.getOrNull()
                if (v != null) {
                    val chime = runCatching { withTimeoutOrNull(6_000) { api.getHourlyChime() } }.getOrNull()
                    chime?.let { org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "hourly_chime", it) }
                    _state.value = State(is24h = v, hourlyChime = chime ?: _state.value.hourlyChime)
                    org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "time_24h", v)
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.set(context, v) // the whole app follows the watch
                    return@launch
                }
                kotlinx.coroutines.delay(3_000L * (attempt + 1))
            }
        }
    }

    /**
     * Best-effort readback after a 0x13 write: the watch may ignore a GET sent right after a SET,
     * so wait a moment first, and treat no answer as null (not an error) — the write already succeeded.
     */
    private suspend fun <T> confirmRead(read: suspend () -> T?): T? {
        kotlinx.coroutines.delay(500)
        return try {
            kotlinx.coroutines.withTimeoutOrNull(12_000) { read() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.let { if (!it.isActive) throw e } // real cancellation (screen left)
            null // an inner timeout, not ours
        } catch (e: Exception) {
            null
        }
    }

    /** Hourly time signal (moved here from the Custom tab); same 0x13 packet as the time format. */
    fun setHourlyChime(on: Boolean) {
        viewModelScope.launch {
            val before = _state.value
            _state.value = before.copy(hourlyChime = on, busy = true)
            runCatching {
                kotlinx.coroutines.withTimeout(20_000) { api.setHourlyChime(on) }
                confirmRead { api.getHourlyChime() }
            }.onSuccess { v ->
                v?.let { org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "hourly_chime", it) }
                _state.value = _state.value.copy(hourlyChime = v ?: on, busy = false)
            }.onFailure {
                _state.value = before.copy(busy = false)
                AppSnackbar("Hourly time signal: ${it.message}")
            }
        }
    }

    fun set(is24h: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(is24h = is24h, busy = true) // move the UI immediately
            runCatching {
                kotlinx.coroutines.withTimeout(20_000) { api.setTimeFormat24h(is24h) }
                confirmRead { api.getTimeFormat24h() }
            }
                .onSuccess {
                    // Readback unanswered: the write itself went through, so trust it.
                    val v = it ?: is24h
                    _state.value = _state.value.copy(is24h = v, busy = false)
                    org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "time_24h", v)
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.set(context, v)
                    if (it != null && it != is24h) AppSnackbar("Time format: watch kept ${if (it) "24h" else "12h"}")
                }
                .onFailure {
                    _state.value = _state.value.copy(is24h = !is24h, busy = false)
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
        androidx.compose.foundation.layout.Column {
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
        // Hourly time signal (moved here from the Custom tab)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(text = "Hourly time signal")
            if (s.hourlyChime == null) org.avmedia.gshockGoogleSync.ui.common.LoadingSpinner()
            else AppSwitch(checked = s.hourlyChime == true, enabled = !s.busy, onCheckedChange = { vm.setHourlyChime(it) })
        }
        }
    }
}
