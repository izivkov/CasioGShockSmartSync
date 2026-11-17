package org.avmedia.gshockGoogleSync.h5600

import AppSwitch
import AppText
import android.app.AlertDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppSmallButton
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.AppTimeFormat
import org.avmedia.gshockGoogleSync.h5600.AlarmPool.PhoneAlarm
import timber.log.Timber
import javax.inject.Inject

/** The phone-side alarm list (any number of alarms) shown in the Alarms tab for DW-H5600 watches. */
@HiltViewModel
class H5600AlarmPoolViewModel @Inject constructor(
    private val api: GShockRepository,
    private val runner: H5600ScheduleRunner,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    data class UiState(
        val supported: Boolean = false,
        val busy: Boolean = false, // only the first-use import from the watch
        /** Watch update running or queued in the background ("sending to watch…"); the UI stays usable. */
        val syncing: Boolean = false,
        /** The last watch update failed; it is retried automatically (the phone list stays as edited). */
        val pending: Boolean = false,
        val alarms: List<PhoneAlarm> = emptyList(),
        val onWatch: Set<String> = emptySet(),
        val onWatchSmart: String? = null,
        val lastApplied: String? = null,
        /** Alarms that may be snoozing on the watch right now (offer "Stop snooze"). */
        val snoozing: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(UiState(supported = api.isH5600SettingsSupported()))
    val state: StateFlow<UiState> = _state

    init {
        if (_state.value.supported) {
            val pool = AlarmPool.load(context)
            if (pool != null) reload() else importFromWatch()
        }
    }

    private fun reload() = _state.update {
        it.copy(
            alarms = AlarmPool.load(context).orEmpty().sortedBy { a -> a.minuteOfDay },
            onWatch = AlarmPool.loadedIds(context).filterNotNull().toSet(),
            onWatchSmart = AlarmPool.loadedSmartId(context),
            lastApplied = H5600Schedule.lastApplied(context),
            snoozing = AlarmPool.load(context)?.let { p -> AlarmPool.snoozing(p, java.time.LocalDateTime.now()).map { a -> a.first.id }.toSet() }.orEmpty(),
        )
    }

    /** Cancels the snooze on the watch (time sync) and ends the alarm's hold — in the background. */
    fun stopSnooze(id: String) {
        _state.update { it.copy(snoozing = it.snoozing - id) } // the button goes away at once
        SnoozeNotifier.cancel(context, id)
        runner.requestStopSnooze(id)
    }

    /** First use: the list starts with the watch's current alarms and earlier per-alarm settings. */
    private fun importFromWatch() = work("import alarms") {
        val watch = api.getAlarms()
        AlarmPool.save(context, AlarmPool.importFrom(watch, AlarmDayRules.load(context), AlarmDayRules.loadVibeOnly(context)))
        reload()
        runner.requestApplyAlarms()
    }

    /**
     * Saves the change on the phone and shows it at once; the watch is updated in the background
     * (queued, quick successive edits merged into one update).
     */
    private fun change(transform: (List<PhoneAlarm>) -> List<PhoneAlarm>) {
        AlarmPool.save(context, transform(AlarmPool.load(context).orEmpty()))
        reload()
        runner.requestApplyAlarms()
    }

    /** New alarm from the editor, with all its options: one watch update. */
    fun add(a: PhoneAlarm) = change { it + AlarmPool.arm(a, java.time.LocalDateTime.now()) }
    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    /**
     * Saved from the editor. A one-time alarm is switched on and (re)set for the next time its hh:mm
     * comes round — editing it means you want it to ring.
     */
    fun update(a: PhoneAlarm) = change { list ->
        val saved = if (a.once) a.copy(enabled = true) else a
        list.map { if (it.id == a.id) AlarmPool.arm(saved, java.time.LocalDateTime.now()) else it }
    }

    /** Card switch; switching a one-time alarm back on sets it for its next hh:mm. */
    fun setEnabled(a: PhoneAlarm, on: Boolean) = change { list ->
        list.map { if (it.id != a.id) it else if (on) AlarmPool.arm(it.copy(enabled = true), java.time.LocalDateTime.now()) else it.copy(enabled = false) }
    }

    init {
        runner.onPoolChanged = { reload() } // after each watch pass: on-watch tags, snooze, one-time alarms
        viewModelScope.launch { runner.syncing.collect { on -> _state.update { it.copy(syncing = on) }; if (!on) reload() } }
        viewModelScope.launch { runner.alarmsPending.collect { p -> _state.update { it.copy(pending = p) } } }
    }

    override fun onCleared() {
        runner.onPoolChanged = null
    }

    private fun work(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e // screen left: not a failure
                Timber.e(e, "H5600 $what timed out")
                AppSnackbar("Alarms: $what timed out")
            } catch (e: Exception) {
                Timber.e(e, "H5600 $what failed")
                AppSnackbar("Alarms: $what failed: ${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

/** Replaces the stock 4-slot alarm list for DW-H5600 watches. Tap an alarm to edit all its options at once. */
@Composable
fun H5600AlarmPoolList(vm: H5600AlarmPoolViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    if (!s.supported) return
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val is24h by AppTimeFormat.is24h.collectAsState() // recompose on change
    var editing by remember { mutableStateOf<PhoneAlarm?>(null) }
    var isNew by remember { mutableStateOf(false) }

    editing?.let { a ->
        AlarmEditorDialog(
            initial = a,
            isNew = isNew,
            onSave = { edited -> if (isNew) vm.add(edited) else vm.update(edited); editing = null },
            onDismiss = { editing = null },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        s.alarms.forEach { a ->
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { isNew = false; editing = a },
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(text = AppTimeFormat.hm(a.hour, a.minute), fontSize = 32.sp)
                            if (a.label.isNotEmpty()) AppText(text = a.label, fontSize = 15.sp)
                        }
                        if (a.id in s.onWatch) AppText(text = "on watch", fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
                        if (a.id == s.onWatchSmart) AppText(text = "on watch (smart)", fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
                        AppSwitch(checked = a.enabled, onCheckedChange = { vm.setEnabled(a, it) })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(text = optionsSummary(a), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (a.id in s.snoozing) AppSmallButton(
                            text = "Stop snooze",
                            onClick = { vm.stopSnooze(a.id) },
                        )
                        AppSmallButton(
                            text = "Delete",
                            onClick = {
                                AlertDialog.Builder(context)
                                    .setTitle("Delete " + AppTimeFormat.hm(a.hour, a.minute) + (if (a.label.isEmpty()) "" else " (${a.label})") + "?")
                                    .setPositiveButton("Delete") { _, _ -> vm.delete(a.id) }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            },
                        )
                    }
                }
            }
        }
        AppButton(
            text = "+ Add alarm",
            enabled = !s.busy, // only while the alarms are first imported from the watch
            onClick = { isNew = true; editing = PhoneAlarm(hour = 7, minute = 0) },
            modifier = Modifier.padding(8.dp),
        )
        AppText(
            text = "Any number of alarms. The watch holds ${AlarmPool.SLOTS}, so the phone keeps the alarms " +
                "due in the next 24 h on the watch (\"on watch\") and swaps them as they ring, and sets " +
                "Vibe only or your Custom mode for the alarm due next (held ${AlarmDayRules.SNOOZE_HOLD_MIN} min " +
                "after an alarm with snooze, 1 min without). Needs the watch connected; alarms set on the watch itself are overwritten.",
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp),
        )
        // The floating snooze card needs "Display over other apps"; re-checked when the app resumes.
        var overlayAllowed by remember { mutableStateOf(SnoozeOverlay.canShow(context)) }
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) overlayAllowed = SnoozeOverlay.canShow(context)
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        if (!overlayAllowed && s.alarms.any { it.snooze && it.enabled }) {
            AppText(
                text = "To show the Stop snooze card on top of other apps, allow \"Display over other apps\".",
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            AppSmallButton(text = "Allow snooze pop-up", onClick = { SnoozeOverlay.requestPermission(context) }, modifier = Modifier.padding(horizontal = 8.dp))
        }
        when {
            s.syncing -> AppText(text = "Sending to watch…", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            s.pending -> AppText(
                text = "Not on the watch yet — retrying automatically (also on the next connection).",
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        s.lastApplied?.let { AppText(text = "Last applied: $it", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
    }
}
