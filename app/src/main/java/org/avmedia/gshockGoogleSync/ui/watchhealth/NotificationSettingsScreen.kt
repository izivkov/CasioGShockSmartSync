package org.avmedia.gshockGoogleSync.ui.watchhealth

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.AppSubScreen
import org.avmedia.gshockapi.io.NotificationSoundCodec

private enum class Picker { Pattern }

/** Sound and vibration mode, per-event switches and the sound pattern (0x42); language and off at night (0x3D). */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    NotificationSettingsContent(ui, onBack, viewModel)
}

@Composable
internal fun NotificationSettingsContent(
    ui: NotificationSettingsUi,
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    val can = !ui.working
    val s = ui.sounds
    AppSubScreen(title = "Notification settings", onBack = onBack) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
            AppText(text = NOTIFICATION_SOUNDS_INTRO, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            ModeRow("Sound", s?.soundMode, can && s != null, viewModel::setSoundMode)
            ModeRow("Vibration", s?.vibrationMode, can && s != null, viewModel::setVibrationMode)
            // The GBX-H5600 sends no pattern byte: the row is hidden then (a read that failed still shows "-").
            if (s == null || s.pattern != null) {
                ValueRow("Sound pattern", s?.pattern?.let { NotificationSoundCodec.PATTERNS[it] } ?: "-", can && s != null) { picker = Picker.Pattern }
            }
            val soundCustom = s?.soundMode == NotificationSoundCodec.CUSTOM
            val vibrationCustom = s?.vibrationMode == NotificationSoundCodec.CUSTOM
            // Per-event switches only in Custom: in Off or On they do nothing and only looked like "all off".
            if (s != null && (soundCustom || vibrationCustom)) {
                AppText(text = "Custom: choose per event", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                NotificationSoundCodec.EVENTS.forEachIndexed { event, name ->
                    EventRow(
                        name = name,
                        sound = if (soundCustom) s.soundOn(event) else null,
                        vibration = if (vibrationCustom) s.vibrationOn(event) else null,
                        enabled = can,
                        onSound = { viewModel.setSoundEvent(event, it) },
                        onVibration = { viewModel.setVibrationEvent(event, it) },
                    )
                }
            }
            val text = if (ui.working) "Talking to the watch..." else ui.message
            if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
        }
        ButtonsRow(buttons = listOf(ButtonData("Read again", viewModel::refresh, enabled = can)))
    }
    val close = { picker = null }
    when (picker) {
        Picker.Pattern -> s?.pattern?.let { current -> ChoiceDialog("Sound pattern", NotificationSoundCodec.PATTERNS, current, { close(); viewModel.setPattern(it) }, close) } ?: Unit
        null -> Unit
    }
}

internal const val NOTIFICATION_SOUNDS_INTRO =
    "How the watch beeps and vibrates for calls, notifications, alerts, timers, the step reminder and the goal."

/** The three modes in the order a person reads them; the values are the watch's (0 off, 2 on, 1 custom). */
internal val MODE_CHOICES = listOf(0 to "Off", 2 to "On (all events)", NotificationSoundCodec.CUSTOM to "Custom (per event)")

@Composable
private fun ModeRow(title: String, mode: Int?, enabled: Boolean, onChoose: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        AppTextLarge(text = title)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            MODE_CHOICES.forEach { (value, label) ->
                FilterChip(
                    selected = mode == value, enabled = enabled, onClick = { onChoose(value) },
                    label = { Text(label) }, modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

/** One event; a null switch is not shown (its mode is not Custom). */
@Composable
private fun EventRow(
    name: String,
    sound: Boolean?,
    vibration: Boolean?,
    enabled: Boolean,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            AppTextLarge(text = name)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (sound != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(text = "Sound", modifier = Modifier.padding(end = 8.dp))
                    AppSwitch(checked = sound, onCheckedChange = onSound, enabled = enabled)
                }
                if (vibration != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(text = "Vibration", modifier = Modifier.padding(end = 8.dp))
                    AppSwitch(checked = vibration, onCheckedChange = onVibration, enabled = enabled)
                }
            }
        }
    }
}
