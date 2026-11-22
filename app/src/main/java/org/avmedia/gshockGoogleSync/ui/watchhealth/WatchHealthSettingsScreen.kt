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
import org.avmedia.gshockapi.io.LengthUnit
import org.avmedia.gshockapi.io.PhysicalSettingsCodec

/** Which dialog is open. */
internal sealed class Editing {
    data object HeartRate : Editing()
    data object Bedtime : Editing()
    data object WakeUp : Editing()
    data object NightStart : Editing()
    data object NightEnd : Editing()
    data object LengthUnit : Editing()
    data object StepGoal : Editing()
    data object Height : Editing()
    data object Weight : Editing()
    data object BirthDate : Editing()
    data object Background : Editing()
    data object MaxHr : Editing()
    data object RestingHr : Editing()
    data object Vo2 : Editing()
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
                    ProfileSection(ui) { editing = it }
                    val hr = ui.heartRate
                    ValueRow("Heart rate measurement", hr?.let { HEART_RATE_MODES[it.mode] } ?: "-", can && hr != null) { editing = Editing.HeartRate }
                    val sleep = ui.sleep
                    ValueRow("Bedtime", sleep?.let { hhmm(it.bedtime) } ?: "-", can && sleep != null) { editing = Editing.Bedtime }
                    ValueRow("Wake-up time", sleep?.let { hhmm(it.wakeUp) } ?: "-", can && sleep != null) { editing = Editing.WakeUp }
                    val basic = ui.basic
                    ValueRow("Night window starts", basic?.let { hhmm(it.nightStart) } ?: "-", can && basic != null) { editing = Editing.NightStart }
                    ValueRow("Night window ends", basic?.let { hhmm(it.nightEnd) } ?: "-", can && basic != null) { editing = Editing.NightEnd }
                    SwitchRow("Step reminder", basic?.stepReminder ?: false, can && basic != null) { viewModel.setStepReminder(it) }
                    ValueRow(
                        title = "Length unit",
                        value = basic?.unit?.label ?: "-",
                        enabled = can && basic != null,
                        note = "Check on the watch: which value shows miles is likely, not confirmed",
                    ) { editing = Editing.LengthUnit }
                    ValueRow("Step goal", ui.stepGoal?.toString() ?: "-", can && ui.stepGoal != null) { editing = Editing.StepGoal }
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
    val body = ui.body
    val unit = ui.basic?.unit ?: LengthUnit.KM
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
        Editing.LengthUnit ->
            if (basic != null) {
                val units = org.avmedia.gshockapi.io.LengthUnit.entries
                ChoiceDialog("Length unit", units.map { it.label }, units.indexOf(basic.unit), { close(); viewModel.setLengthUnit(units[it]) }, close)
            }
        Editing.StepGoal ->
            ui.stepGoal?.let {
                NumberDialog("Step goal", it, StepGoalCodec.MIN_STEPS..StepGoalCodec.MAX_STEPS, { v -> close(); viewModel.setStepGoal(v) }, close)
            }
        Editing.Height ->
            if (profile != null) {
                val r = BodyUnits.heightRange(unit)
                NumberDialog("Height, ${if (unit == LengthUnit.MILES) "in" else "cm"}", BodyUnits.heightShown(profile.heightCm, unit), r, { v -> close(); viewModel.setHeightAndWeight(BodyUnits.heightStored(v, unit), profile.weightKg) }, close)
            }
        Editing.Weight ->
            if (profile != null) {
                val r = BodyUnits.weightRange(unit)
                NumberDialog("Weight, ${if (unit == LengthUnit.MILES) "lb" else "kg"}", BodyUnits.weightShown(profile.weightKg, unit), r, { v -> close(); viewModel.setHeightAndWeight(profile.heightCm, BodyUnits.weightStored(v, unit)) }, close)
            }
        Editing.BirthDate -> if (profile != null) BirthDateDialog(profile, close, viewModel)
        Editing.Background ->
            if (body != null) {
                ChoiceDialog("Training background", PhysicalSettingsCodec.TRAINING_BACKGROUNDS, body.trainingBackground, { close(); viewModel.setTrainingBackground(it) }, close)
            }
        Editing.MaxHr ->
            if (body != null) NumberDialog("Highest heart rate, bpm", body.maxHr, PhysicalSettingsCodec.MAX_HR_RANGE, { close(); viewModel.setMaxHeartRate(it) }, close)
        Editing.RestingHr ->
            if (body != null) NumberDialog("Resting heart rate, bpm", body.restingHr, PhysicalSettingsCodec.RESTING_HR_RANGE, { close(); viewModel.setRestingHeartRate(it) }, close)
        Editing.Vo2 ->
            if (body != null) NumberDialog("VO2max", body.vo2max, PhysicalSettingsCodec.VO2MAX_RANGE, { close(); viewModel.setVo2max(it) }, close)
        null -> Unit
    }
}
