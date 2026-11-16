package org.avmedia.gshockGoogleSync.h5600

import AppSwitch
import AppText
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.h5600.AlarmPool.PhoneAlarm
import timber.log.Timber
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
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
        val busy: Boolean = false,
        val alarms: List<PhoneAlarm> = emptyList(),
        val onWatch: Set<String> = emptySet(),
        val onWatchSmart: String? = null,
        val lastApplied: String? = null,
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
        )
    }

    /** First use: the list starts with the watch's current alarms and earlier per-alarm settings. */
    private fun importFromWatch() = work("import alarms") {
        val watch = api.getAlarms()
        AlarmPool.save(context, AlarmPool.importFrom(watch, AlarmDayRules.load(context), AlarmDayRules.loadVibeOnly(context)))
        runner.applyIfEnabled()
        reload()
    }

    private fun change(transform: (List<PhoneAlarm>) -> List<PhoneAlarm>) = work("update alarms") {
        AlarmPool.save(context, transform(AlarmPool.load(context).orEmpty()))
        reload()
        runner.applyIfEnabled() // reload the watch's slots + sound mode now
        reload()
    }

    fun add(hour: Int, minute: Int) = change { it + PhoneAlarm(hour = hour, minute = minute) }
    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }
    fun update(a: PhoneAlarm) = change { list -> list.map { if (it.id == a.id) a else it } }

    private fun work(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "H5600 $what failed")
                AppSnackbar("Alarms: $what failed: ${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

private fun pickTime(context: Context, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, hour, minute, org.avmedia.gshockGoogleSync.utils.AppTimeFormat.current()).show()
}

private fun editLabel(context: Context, a: PhoneAlarm, onSaved: (String) -> Unit) {
    val input = android.widget.EditText(context).apply {
        setText(a.label)
        hint = "e.g. Feed the cats"
        setSingleLine()
        setSelection(text.length)
    }
    val pad = (16 * context.resources.displayMetrics.density).toInt()
    val box = android.widget.FrameLayout(context).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
    AlertDialog.Builder(context)
        .setTitle("Label for " + org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(a.hour, a.minute))
        .setView(box)
        .setPositiveButton("Save") { _, _ -> onSaved(input.text.toString().trim().take(40)) }
        .setNeutralButton("Clear") { _, _ -> onSaved("") }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun pickDays(context: Context, a: PhoneAlarm, onPicked: (Set<DayOfWeek>) -> Unit) {
    val days = DayOfWeek.entries // Mon..Sun
    val checked = BooleanArray(days.size) { days[it] in a.days }
    AlertDialog.Builder(context)
        .setTitle(org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(a.hour, a.minute) + (if (a.label.isEmpty()) "" else " ${a.label}") + " rings on")
        .setMultiChoiceItems(days.map { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }.toTypedArray(), checked) { _, d, on ->
            checked[d] = on
        }
        .setPositiveButton("Save") { _, _ ->
            onPicked(days.filterIndexed { d, _ -> checked[d] }.toSet().ifEmpty { AlarmDayRules.ALL_DAYS })
        }
        .setNeutralButton("Every day") { _, _ -> onPicked(AlarmDayRules.ALL_DAYS) }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Replaces the stock 4-slot alarm list for DW-H5600 watches. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun H5600AlarmPoolList(vm: H5600AlarmPoolViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    if (!s.supported) return
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val is24h by org.avmedia.gshockGoogleSync.utils.AppTimeFormat.is24h.collectAsState() // recompose on change

    Column(modifier = Modifier.fillMaxWidth()) {
        s.alarms.forEach { a ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                text = org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(a.hour, a.minute),
                                fontSize = 32.sp,
                                modifier = Modifier.clickable(enabled = !s.busy) {
                                    pickTime(context, a.hour, a.minute) { h, m -> vm.update(a.copy(hour = h, minute = m)) }
                                },
                            )
                            AppText(
                                text = a.label.ifEmpty { "Add label" },
                                fontSize = 15.sp,
                                color = if (a.label.isEmpty()) androidx.compose.ui.graphics.Color.Gray
                                else androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = Modifier.clickable(enabled = !s.busy) {
                                    editLabel(context, a) { l -> vm.update(a.copy(label = l)) }
                                },
                            )
                        }
                        if (a.id in s.onWatch) AppText(text = "on watch", fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
                        if (a.id == s.onWatchSmart) AppText(text = "on watch (smart)", fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
                        AppSwitch(
                            checked = a.enabled,
                            enabled = !s.busy,
                            onCheckedChange = { vm.update(a.copy(enabled = it)) },
                        )
                    }
                    // All options in one wrapping row of compact buttons
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(top = 6.dp), // a little air below the label
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                            text = AlarmDayRules.label(a.days),
                            enabled = !s.busy,
                            onClick = { pickDays(context, a) { d -> vm.update(a.copy(days = d)) } },
                        )
                        org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                            text = if (a.vibeOnly) "Vibe only" else "Sound + vibe",
                            enabled = !s.busy,
                            onClick = { vm.update(a.copy(vibeOnly = !a.vibeOnly)) },
                        )
                        org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                            text = if (a.isSmart) "Smart −${a.smartWindowMin} min" else "Smart off",
                            enabled = !s.busy,
                            onClick = {
                                val options = listOf(0, 15, 30, 45, 60)
                                val labels = options.map { if (it == 0) "Off (normal alarm)" else "Up to $it min before" }
                                AlertDialog.Builder(context)
                                    .setTitle("Smart alarm: wake in light sleep")
                                    .setSingleChoiceItems(labels.toTypedArray(), options.indexOf(a.smartWindowMin).coerceAtLeast(0)) { d, i ->
                                        vm.update(a.copy(smartWindowMin = options[i])); d.dismiss()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            },
                        )
                        org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                            text = if (a.snooze) "Snooze on" else "Snooze off",
                            enabled = !s.busy,
                            onClick = { vm.update(a.copy(snooze = !a.snooze)) },
                        )
                        org.avmedia.gshockGoogleSync.ui.common.AppSmallButton(
                            text = "Delete",
                            enabled = !s.busy,
                            onClick = {
                                AlertDialog.Builder(context)
                                    .setTitle("Delete " + org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(a.hour, a.minute) + (if (a.label.isEmpty()) "" else " (${a.label})") + "?")
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
            enabled = !s.busy,
            onClick = { pickTime(context, 7, 0) { h, m -> vm.add(h, m) } },
            modifier = Modifier.padding(8.dp),
        )
        AppText(
            text = "Any number of alarms. The watch holds ${AlarmPool.SLOTS}, so the phone keeps the alarms " +
                "due in the next 24 h on the watch (\"on watch\") and swaps them as they ring, and sets " +
                "Vibe only or your Custom mode for the alarm due next (held ${AlarmDayRules.SNOOZE_HOLD_MIN} min " +
                "for snooze). Needs the watch connected; alarms set on the watch itself are overwritten.",
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp),
        )
        s.lastApplied?.let { AppText(text = "Last applied: $it", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
    }
}
