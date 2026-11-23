package org.avmedia.gshockGoogleSync.ui.watchhealth

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.io.NotificationManagerCodec
import org.avmedia.gshockapi.io.NotificationSoundCodec

private enum class Picker { SoundMode, VibrationMode, Pattern, Language, OffAtNight }

private val NUMBERED = (0..3).map { "Setting $it" }

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
    val m = ui.manager
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Notification settings", Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    ValueRow("Sound", s?.let { NotificationSoundCodec.MODES[it.soundMode] } ?: "-", can && s != null) { picker = Picker.SoundMode }
                    ValueRow("Vibration", s?.let { NotificationSoundCodec.MODES[it.vibrationMode] } ?: "-", can && s != null) { picker = Picker.VibrationMode }
                    // The owner's watch sends no pattern byte: the row is hidden then (a read that failed still shows "-").
                    if (s == null || s.pattern != null) {
                        ValueRow("Sound pattern", s?.pattern?.let { NotificationSoundCodec.PATTERNS[it] } ?: "-", can && s != null) { picker = Picker.Pattern }
                    }
                    AppText(text = "Events (the switches apply in Custom mode)", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    NotificationSoundCodec.EVENTS.forEachIndexed { event, name ->
                        EventRow(
                            name = name,
                            sound = s?.soundOn(event) ?: false,
                            vibration = s?.vibrationOn(event) ?: false,
                            soundEnabled = can && s != null && s.soundMode == NotificationSoundCodec.CUSTOM,
                            vibrationEnabled = can && s != null && s.vibrationMode == NotificationSoundCodec.CUSTOM,
                            onSound = { viewModel.setSoundEvent(event, it) },
                            onVibration = { viewModel.setVibrationEvent(event, it) },
                        )
                    }
                    ValueRow("Language", m?.let { "Setting ${it.language}" } ?: "-", can && m != null, note = "Meaning of the values: $NOT_CONFIRMED") { picker = Picker.Language }
                    ValueRow("Off at night", m?.let { "Setting ${it.offAtNight}" } ?: "-", can && m != null, note = "Meaning of the values: $NOT_CONFIRMED") { picker = Picker.OffAtNight }
                    ValueRow("Pop-up size", m?.popUpSize?.let { "Value $it" } ?: "-", enabled = false, note = "Read only")
                    val text = if (ui.working) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
                }
                ButtonsRow(buttons = listOf(ButtonData("Read again", viewModel::refresh, enabled = can), ButtonData("Back", onBack)))
            }
        }
    }
    val close = { picker = null }
    when (picker) {
        Picker.SoundMode -> if (s != null) ChoiceDialog("Sound", NotificationSoundCodec.MODES, s.soundMode, { close(); viewModel.setSoundMode(it) }, close)
        Picker.VibrationMode -> if (s != null) ChoiceDialog("Vibration", NotificationSoundCodec.MODES, s.vibrationMode, { close(); viewModel.setVibrationMode(it) }, close)
        Picker.Pattern -> if (s?.pattern != null) ChoiceDialog("Sound pattern", NotificationSoundCodec.PATTERNS, s.pattern, { close(); viewModel.setPattern(it) }, close)
        Picker.Language -> if (m != null) ChoiceDialog("Language", NUMBERED, m.language, { close(); viewModel.setLanguage(it) }, close)
        Picker.OffAtNight -> if (m != null) ChoiceDialog("Off at night", NUMBERED, m.offAtNight, { close(); viewModel.setOffAtNight(it) }, close)
        null -> Unit
    }
}

@Composable
private fun EventRow(
    name: String,
    sound: Boolean,
    vibration: Boolean,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            AppTextLarge(text = name)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.alpha(if (soundEnabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
                    AppText(text = "Sound", modifier = Modifier.padding(end = 8.dp))
                    AppSwitch(checked = sound, onCheckedChange = onSound, enabled = soundEnabled)
                }
                Row(modifier = Modifier.alpha(if (vibrationEnabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
                    AppText(text = "Vibration", modifier = Modifier.padding(end = 8.dp))
                    AppSwitch(checked = vibration, onCheckedChange = onVibration, enabled = vibrationEnabled)
                }
            }
        }
    }
}
