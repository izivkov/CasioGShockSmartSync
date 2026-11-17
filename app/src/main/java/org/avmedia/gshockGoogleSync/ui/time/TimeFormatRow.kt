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
import kotlinx.coroutines.sync.withLock
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
            // One 0x13 read gives both bits (the library already retries unanswered reads); the
            // outer retries cover the watch not being connected yet when the tab opens.
            repeat(3) { attempt ->
                val bits = confirmRead(delayMs = 0) { api.getTimeFormatAndChime() }
                if (bits != null) {
                    val (v, chime) = bits
                    org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "hourly_chime", chime)
                    _state.value = State(is24h = v, hourlyChime = chime)
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
    private suspend fun <T> confirmRead(delayMs: Long = 500, read: suspend () -> T?): T? {
        kotlinx.coroutines.delay(delayMs)
        return try {
            kotlinx.coroutines.withTimeoutOrNull(12_000) { read() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.let { if (!it.isActive) throw e } // real cancellation (screen left)
            null // an inner timeout, not ours
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Readback until the watch reports [expected]: the watch often sends each 0x13 packet twice
     * (~0.8 s apart), so the first read after a write can still be the OLD packet. Up to 3 reads,
     * 1.2 s apart; returns the last value read, or null if none answered.
     */
    private suspend fun <T> confirmValue(expected: T, read: suspend () -> T?): T? {
        var last: T? = null
        repeat(3) { i ->
            val v = confirmRead(delayMs = if (i == 0) 500 else 1_200, read = read)
            if (v != null) last = v
            if (v == expected) return v
        }
        return last
    }

    // Taps are shown at once and sent one after another in the background; the controls stay
    // usable. A change that fails is rolled back to what the watch has, with a message.
    private val queue = kotlinx.coroutines.sync.Mutex()

    private fun queued(block: suspend () -> Unit) {
        viewModelScope.launch { queue.withLock { block() } }
    }

    /** Hourly time signal (moved here from the Custom tab); same 0x13 packet as the time format. */
    fun setHourlyChime(on: Boolean) {
        val before = _state.value.hourlyChime
        _state.value = _state.value.copy(hourlyChime = on)
        queued {
            try {
                kotlinx.coroutines.withTimeout(20_000) { api.setHourlyChime(on) }
                val v = confirmValue(on) { api.getHourlyChime() } ?: on
                org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "hourly_chime", v)
                _state.value = _state.value.copy(hourlyChime = v)
                if (v != on) AppSnackbar("Hourly time signal: the watch kept it ${if (v) "on" else "off"}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                _state.value = _state.value.copy(hourlyChime = before)
                AppSnackbar("Hourly time signal: the watch did not answer — not changed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(hourlyChime = before)
                AppSnackbar("Hourly time signal: ${e.message} — not changed")
            }
        }
    }

    fun set(is24h: Boolean) {
        val before = _state.value.is24h
        _state.value = _state.value.copy(is24h = is24h) // move the UI immediately
        queued {
            try {
                kotlinx.coroutines.withTimeout(20_000) { api.setTimeFormat24h(is24h) }
                // Readback unanswered: the write itself went through, so trust it.
                val read = confirmValue(is24h) { api.getTimeFormat24h() }
                val v = read ?: is24h
                _state.value = _state.value.copy(is24h = v)
                org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "time_24h", v)
                org.avmedia.gshockGoogleSync.utils.AppTimeFormat.set(context, v)
                if (read != null && read != is24h) AppSnackbar("Time format: the watch kept ${if (read) "24h" else "12h"}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                _state.value = _state.value.copy(is24h = before)
                AppSnackbar("Time format: the watch did not answer — not changed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(is24h = before)
                AppSnackbar("Time format: ${e.message} — not changed")
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
                AppChoiceButton(text = "12h", selected = s.is24h == false, onClick = { vm.set(false) })
                AppChoiceButton(text = "24h", selected = s.is24h == true, onClick = { vm.set(true) })
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
            else AppSwitch(checked = s.hourlyChime == true, onCheckedChange = { vm.setHourlyChime(it) })
        }
        }
    }
}
