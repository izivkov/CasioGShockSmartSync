package org.avmedia.gshockGoogleSync.ui.watchhealth

import AppText
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.io.StepGoalCodec

/** Which dialog is open. */
private sealed class Editing {
    data object HeartRate : Editing()
    data object Bedtime : Editing()
    data object WakeUp : Editing()
    data object NightStart : Editing()
    data object NightEnd : Editing()
    data object StepGoal : Editing()
    data object Height : Editing()
    data object Weight : Editing()
}

private val HEART_RATE_MODES = listOf("Off", "Only during activities", "Always")

/** Heart-rate measurement, sleep times, night window, step reminder, unit, step goal, profile; smart alarm shown only. */
@Composable
fun WatchHealthSettingsScreen(
    onBack: () -> Unit,
    viewModel: WatchHealthSettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    WatchHealthSettingsContent(ui, onBack, viewModel)
}

@Composable
internal fun WatchHealthSettingsContent(
    ui: WatchHealthUi,
    onBack: () -> Unit,
    viewModel: WatchHealthSettingsViewModel,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    val can = !ui.working
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Watch health settings", Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    val hr = ui.heartRate
                    ValueRow("Heart rate measurement", hr?.let { HEART_RATE_MODES[it.mode] } ?: "-", can && hr != null) { editing = Editing.HeartRate }
                    val sleep = ui.sleep
                    ValueRow("Bedtime", sleep?.let { hhmm(it.bedtime) } ?: "-", can && sleep != null) { editing = Editing.Bedtime }
                    ValueRow("Wake-up time", sleep?.let { hhmm(it.wakeUp) } ?: "-", can && sleep != null) { editing = Editing.WakeUp }
                    val basic = ui.basic
                    ValueRow("Night window starts", basic?.let { hhmm(it.nightStart) } ?: "-", can && basic != null) { editing = Editing.NightStart }
                    ValueRow("Night window ends", basic?.let { hhmm(it.nightEnd) } ?: "-", can && basic != null) { editing = Editing.NightEnd }
                    SwitchRow("Step reminder", basic?.stepReminder ?: false, can && basic != null) { viewModel.setStepReminder(it) }
                    SwitchRow(
                        title = "Length unit: miles",
                        checked = basic?.unitBit ?: false,
                        enabled = can && basic != null,
                        note = "Off is km. Which value means miles is likely, not confirmed",
                    ) { viewModel.setMiles(it) }
                    ValueRow("Step goal", ui.stepGoal?.toString() ?: "-", can && ui.stepGoal != null) { editing = Editing.StepGoal }
                    val profile = ui.profile
                    ValueRow("Height", profile?.let { "${it.heightCm} cm" } ?: "-", can && profile != null) { editing = Editing.Height }
                    ValueRow("Weight", profile?.let { "${it.weightKg} kg" } ?: "-", can && profile != null) { editing = Editing.Weight }
                    ValueRow(
                        title = "Birth date",
                        value = profile?.let { p -> listOfNotNull(p.birthYear, p.birthMonth, p.birthDay).joinToString("-") } ?: "-",
                        enabled = false,
                        note = "Shown only",
                    )
                    val alarm = ui.alarm
                    ValueRow(
                        title = "Smart alarm",
                        value = alarm?.let { "${hhmm(it.hour to it.minute)} ${if (it.on) "on" else "off"}, ${it.windowMinutes} min" } ?: "-",
                        enabled = false,
                        note = "Read only, $NOT_CONFIRMED",
                    )
                    val text = if (ui.working) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
                }
                ButtonsRow(
                    buttons = listOf(
                        ButtonData("Read again", viewModel::refresh, enabled = can),
                        ButtonData("Back", onBack),
                    ),
                )
            }
        }
    }
    EditDialogs(ui, editing, { editing = null }, viewModel)
}

@Composable
private fun EditDialogs(
    ui: WatchHealthUi,
    editing: Editing?,
    close: () -> Unit,
    viewModel: WatchHealthSettingsViewModel,
) {
    val hr = ui.heartRate
    val sleep = ui.sleep
    val basic = ui.basic
    val profile = ui.profile
    when (editing) {
        Editing.HeartRate ->
            if (hr != null) {
                ChoiceDialog("Heart rate measurement", HEART_RATE_MODES, hr.mode, { close(); viewModel.setHeartRateMode(it) }, close)
            }
        Editing.Bedtime ->
            if (sleep != null) TimeDialog(sleep.bedtime.first, sleep.bedtime.second, { h, m -> close(); viewModel.setBedtime(h, m) }, close)
        Editing.WakeUp ->
            if (sleep != null) TimeDialog(sleep.wakeUp.first, sleep.wakeUp.second, { h, m -> close(); viewModel.setWakeUp(h, m) }, close)
        Editing.NightStart ->
            if (basic != null) {
                TimeDialog(basic.nightStart.first, basic.nightStart.second, { h, m -> close(); viewModel.setNightWindow(h to m, basic.nightEnd) }, close)
            }
        Editing.NightEnd ->
            if (basic != null) {
                TimeDialog(basic.nightEnd.first, basic.nightEnd.second, { h, m -> close(); viewModel.setNightWindow(basic.nightStart, h to m) }, close)
            }
        Editing.StepGoal ->
            ui.stepGoal?.let {
                NumberDialog("Step goal", it, StepGoalCodec.MIN_STEPS..StepGoalCodec.MAX_STEPS, { v -> close(); viewModel.setStepGoal(v) }, close)
            }
        Editing.Height ->
            if (profile != null) {
                NumberDialog("Height, cm", profile.heightCm, 100..250, { v -> close(); viewModel.setHeightAndWeight(v, profile.weightKg) }, close)
            }
        Editing.Weight ->
            if (profile != null) {
                NumberDialog("Weight, kg", profile.weightKg, 20..300, { v -> close(); viewModel.setHeightAndWeight(profile.heightCm, v) }, close)
            }
        null -> Unit
    }
}
