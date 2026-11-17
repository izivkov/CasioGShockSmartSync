package org.avmedia.gshockGoogleSync.h5600

import AppSwitch
import AppText
import android.app.AlertDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
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
    // Hold-and-drag reordering
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    val heights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
        // Live drop position while dragging: the cards in between slide out of the way.
        val fromIndex = s.alarms.indexOfFirst { it.id == dragId }
        val target = if (fromIndex < 0) -1 else dropIndex(fromIndex, dragY, s.alarms.map { heights[it.id] ?: 0 }, spacingPx)
        val draggedSize = if (fromIndex < 0) 0f else (heights[dragId] ?: 0) + spacingPx
        androidx.compose.runtime.LaunchedEffect(target) {
            if (fromIndex >= 0) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
        s.alarms.forEachIndexed { index, a -> androidx.compose.runtime.key(a.id) {
            val dragging = dragId == a.id
            val makeRoom = when {
                fromIndex < 0 || dragging -> 0f
                fromIndex < target && index in (fromIndex + 1)..target -> -draggedSize // dragged down past it: move up
                fromIndex > target && index in target until fromIndex -> draggedSize  // dragged up past it: move down
                else -> 0f
            }
            val shift by androidx.compose.animation.core.animateFloatAsState(makeRoom, label = "makeRoom")
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    .onGloballyPositioned { heights[a.id] = it.size.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragY else shift
                        scaleX = if (dragging) 1.03f else 1f
                        scaleY = if (dragging) 1.03f else 1f
                        alpha = if (dragging) 0.9f else 1f
                    }
                    // Hold, drag up/down, release: the alarm moves to where it was dropped.
                    .pointerInput(a.id, index, s.alarms.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragId = a.id; dragY = 0f
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount -> change.consume(); dragY += amount.y },
                            onDragEnd = {
                                val to = dropIndex(index, dragY, s.alarms.map { heights[it.id] ?: 0 }, spacingPx)
                                dragId = null; dragY = 0f
                                if (to != index) vm.move(a.id, to)
                            },
                            onDragCancel = { dragId = null; dragY = 0f },
                        )
                    }
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
        } }
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

/**
 * Where a card dragged by [dy] px from position [from] lands: it passes a neighbour once it has
 * moved more than half of that neighbour's height (plus the gap between cards).
 */
internal fun dropIndex(from: Int, dy: Float, heights: List<Int>, gap: Float): Int {
    var to = from
    var rest = dy
    if (rest > 0) {
        while (to < heights.size - 1 && rest > (heights[to + 1] + gap) / 2) { rest -= heights[to + 1] + gap; to++ }
    } else {
        while (to > 0 && -rest > (heights[to - 1] + gap) / 2) { rest += heights[to - 1] + gap; to-- }
    }
    return to
}
