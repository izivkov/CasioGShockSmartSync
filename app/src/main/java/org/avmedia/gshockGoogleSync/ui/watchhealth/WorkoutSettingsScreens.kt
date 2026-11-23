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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.io.ActivityFaceCodec
import org.avmedia.gshockapi.io.ActivityListCodec
import org.avmedia.gshockapi.io.ActivityModels
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.TrainingAlertCodec

/** The Activity settings list: which activities the watch shows and in what order, then one row per activity. */
@Composable
fun WorkoutSettingsListScreen(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    viewModel: WorkoutSettingsListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    WorkoutSettingsListContent(ui, onBack, viewModel::refresh, onOpen, viewModel::setEnabled, viewModel::move)
}

@Composable
internal fun WorkoutSettingsListContent(
    ui: WorkoutListUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onEnable: (Int, Boolean) -> Unit = { _, _ -> },
    onMove: (Int, Int) -> Unit = { _, _ -> },
) {
    val family = ActivityModels.H5600
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Activity settings", Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    val all = ui.activities
                    if (all != null) {
                        val on = ActivityModels.visible(family, all)
                        Section("Activities on the watch")
                        on.forEachIndexed { i, code ->
                            ActivityOnRow(code, i + 1, i > 0, i < on.size - 1, !ui.working, onEnable, onMove)
                        }
                        ActivityModels.offered(family).filter { it !in on }.forEach { code ->
                            ActivityOnRow(code, null, false, false, !ui.working, onEnable, onMove)
                        }
                        Section("Settings of one activity")
                        on.forEach { code -> ValueRow(ActivityModels.name(family, code), ">", !ui.working) { onOpen(code) } }
                    }
                    if (ui.rawHex != null) ValueRow("Raw answer", ui.rawHex, false, note = "Read only")
                    val text = if (ui.working) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
                }
                ButtonsRow(buttons = listOf(ButtonData("Read again", onRefresh, enabled = !ui.working), ButtonData("Back", onBack)))
            }
        }
    }
}

private const val NO_ANSWER = "Not answered"

private fun onOff(on: Boolean) = if (on) "On" else "Off"

/** The settings of one activity. */
@Composable
fun WorkoutActivityScreen(
    activity: Int,
    onBack: () -> Unit,
    viewModel: WorkoutActivityViewModel = hiltViewModel(),
) {
    LaunchedEffect(activity) { viewModel.load(activity) }
    val ui by viewModel.ui.collectAsState()
    WorkoutActivityContent(ui, onBack, viewModel)
}

@Composable
internal fun WorkoutActivityContent(
    ui: WorkoutActivityUi,
    onBack: () -> Unit,
    viewModel: WorkoutActivityViewModel,
) {
    var picker by remember { mutableStateOf<String?>(null) }
    val can = !ui.working
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(ActivityModels.name(ActivityModels.H5600, ui.activity), Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    Section("Auto lap and pause")
                    val run = ui.run
                    SwitchRow("Auto pause", run?.autoPause ?: false, can && run != null) { viewModel.setAutoPause(it) }
                    SwitchRow("Auto run detection", run?.autoRunDetection ?: false, can && run != null) { viewModel.setAutoRunDetection(it) }
                    SwitchRow("Use the phone's location", run?.phoneLocation ?: false, can && run != null) { viewModel.setPhoneLocation(it) }
                    ValueRow("Auto lap distance", run?.lapDistanceRaw?.toString() ?: NO_ANSWER, false, note = "Read only, unit $NOT_CONFIRMED")
                    ValueRow("Auto lap screen", run?.lapScreen?.toString() ?: NO_ANSWER, false, note = "Read only, meaning $NOT_CONFIRMED")
                    Section("Heart rate")
                    val hr = ui.heartRate
                    val flags = ui.heartRateFlags
                    ValueRow("Measurement", hr?.let { HEART_RATE_MODES[it.mode] } ?: NO_ANSWER, can && hr != null) { picker = "hr" }
                    SwitchRow("In-zone notice", flags?.inZoneNotice ?: false, can && flags != null) { viewModel.setInZoneNotice(it) }
                    SwitchRow("Use target", flags?.useTarget ?: false, can && flags != null) { viewModel.setUseTarget(it) }
                    SwitchRow("Zone alert", flags?.zoneAlert ?: false, can && flags != null) { viewModel.setZoneAlert(it) }
                    ValueRow("Target zone and limits", hr?.let { "Zone ${it.zone}, ${it.lowBpm} to ${it.highBpm} bpm" } ?: NO_ANSWER, false, note = "Read only")
                    Section("Training alerts")
                    val alerts = ui.alerts
                    ValueRow("Distance alert", alerts?.let { TrainingAlertCodec.REPEATS[it.distanceRepeat] } ?: NO_ANSWER, can && alerts != null) { picker = "distance" }
                    ValueRow("Calories alert", alerts?.let { TrainingAlertCodec.REPEATS[it.caloriesRepeat] } ?: NO_ANSWER, can && alerts != null) { picker = "calories" }
                    ValueRow("Altitude alert", alerts?.let { TrainingAlertCodec.REPEATS[it.altitudeRepeat] } ?: NO_ANSWER, can && alerts != null) { picker = "altitude" }
                    ValueRow(
                        "Intervals",
                        alerts?.let { "time ${it.time1}, ${it.time2}; distance ${it.distance1}, ${it.distance2}; calories ${it.caloriesInterval}; altitude ${it.altitudeInterval}" } ?: NO_ANSWER,
                        false,
                        note = "Read only, units $NOT_CONFIRMED",
                    )
                    Section("Form alert")
                    ValueRow("Value", ui.formAlert ?: NO_ANSWER, false, note = "Read only, layout $NOT_CONFIRMED")
                    Section("Screens")
                    val screens = ui.screens
                    if (screens == null) {
                        ValueRow("Screens", NO_ANSWER, false)
                    } else {
                        screens.forEachIndexed { i, items ->
                            ValueRow("Screen ${i + 1}", items.joinToString(", ") { ActivityFaceCodec.itemName(it) }, false, note = "Read only")
                        }
                    }
                    val text = if (ui.working) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
                }
                ButtonsRow(buttons = listOf(ButtonData("Read again", viewModel::refresh, enabled = can), ButtonData("Back", onBack)))
            }
        }
    }
    val close = { picker = null }
    val hr = ui.heartRate
    val alerts = ui.alerts
    when (picker) {
        "hr" -> if (hr != null) ChoiceDialog("Heart rate measurement", HEART_RATE_MODES, hr.mode, { close(); viewModel.setHeartRateMode(it) }, close)
        "distance" -> if (alerts != null) ChoiceDialog("Distance alert", TrainingAlertCodec.REPEATS, alerts.distanceRepeat, { close(); viewModel.setDistanceRepeat(it) }, close)
        "calories" -> if (alerts != null) ChoiceDialog("Calories alert", TrainingAlertCodec.REPEATS, alerts.caloriesRepeat, { close(); viewModel.setCaloriesRepeat(it) }, close)
        "altitude" -> if (alerts != null) ChoiceDialog("Altitude alert", TrainingAlertCodec.REPEATS, alerts.altitudeRepeat, { close(); viewModel.setAltitudeRepeat(it) }, close)
        else -> Unit
    }
}

@Composable
private fun Section(title: String) {
    AppText(text = title, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 2.dp))
}

/** One activity of the list: its place (null when off), up and down, and the switch. */
@Composable
private fun ActivityOnRow(
    code: Int,
    place: Int?,
    canUp: Boolean,
    canDown: Boolean,
    enabled: Boolean,
    onEnable: (Int, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextLarge(text = if (place != null) "$place. ${ActivityModels.name(ActivityModels.H5600, code)}" else ActivityModels.name(ActivityModels.H5600, code), modifier = Modifier.weight(1f))
            if (place != null) {
                TextButton(onClick = { onMove(code, -1) }, enabled = enabled && canUp) { Text("Up") }
                TextButton(onClick = { onMove(code, 1) }, enabled = enabled && canDown) { Text("Down") }
            }
            AppSwitch(checked = place != null, onCheckedChange = { onEnable(code, it) }, enabled = enabled)
        }
    }
}
