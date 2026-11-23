package org.avmedia.gshockGoogleSync.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow

/** Registers the watch as a Home Assistant device (mobile_app API) so its values appear as sensors. */
@Composable
fun HaScreen(viewModel: HaViewModel, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    HaScreenContent(
        ui = ui,
        events = HaEvents(
            onBack = onBack,
            onSave = viewModel::save,
            onForget = viewModel::forget,
            onFind = viewModel::findOnNetwork,
            onGroup = viewModel::setGroup,
            onChoose = viewModel::choose,
            onRefreshActions = viewModel::refreshActions,
            onRawSwitch = viewModel::setRawSwitch,
            onCreateDashboard = viewModel::createDashboard,
            onCopyYaml = { viewModel.copyYaml(context) },
            onConfirmName = viewModel::confirmName,
        ),
    )
}

/** The stateless body of [HaScreen]: state in, events out (also used by the screenshot tests). */
@Composable
internal fun HaScreenContent(ui: HaUi, events: HaEvents = HaEvents(), pickerOpen: Boolean = false) {
    var url by remember { mutableStateOf(ui.form.baseUrl) }
    var token by remember { mutableStateOf(ui.form.token) }
    // Before the first registration the name field starts with the watch's own name (without "CASIO ").
    var deviceName by remember {
        mutableStateOf(if (ui.watch.registered) ui.form.deviceName else ui.watch.confirmedName ?: ui.form.deviceName.ifBlank { ui.watch.suggestedName })
    }
    var model by remember { mutableStateOf(ui.form.model) }
    val save = { events.onSave(url, token, deviceName, model) }

    Column(Modifier.fillMaxSize()) {
        HaTitle("Home Assistant")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HaConnectionCard(ui.saved, url, token, { url = it }, { token = it }, ui.check, ui.find, events.onFind, save)
            HaWatchCard(
                ui.saved, ui.watch, deviceName, model, { deviceName = it }, { model = it },
                changed = deviceName != ui.form.deviceName || model != ui.form.model, onSave = save, onRaw = events.onRawSwitch,
                onConfirm = { events.onConfirmName(deviceName, model) },
            )
            HaSentCard(ui.sent, ui.saved, events.onGroup)
            HaButtonCard(ui.saved, ui.actions, events.onChoose, events.onRefreshActions, pickerOpen)
            HaDashboardCard(ui.saved, ui.watch.registered, ui.dash, events.onCreateDashboard, events.onCopyYaml)
        }
        ButtonsRow(
            buttons = listOf(
                ButtonData("Back", events.onBack),
                ButtonData("Forget", { events.onForget(); url = ""; token = ""; deviceName = ""; model = "" }, enabled = ui.saved),
            )
        )
    }
}
