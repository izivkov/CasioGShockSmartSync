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
            onClick = { isNew = true; editing = PhoneAlarm(hour = 7, minute = 0, once = true) },
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
