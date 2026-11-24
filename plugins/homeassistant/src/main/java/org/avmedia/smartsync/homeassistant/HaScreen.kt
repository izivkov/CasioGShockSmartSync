package org.avmedia.smartsync.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
            onRawSwitch = viewModel::setRawSwitch,
            onCreateDashboard = viewModel::createDashboard,
            onCopyYaml = { viewModel.copyYaml(context) },
            onConfirmName = viewModel::confirmName,
        ),
    )
}

/** The stateless body of [HaScreen]: state in, events out (also used by the screenshot tests). */
@Composable
internal fun HaScreenContent(ui: HaUi, events: HaEvents = HaEvents()) {
    var url by remember { mutableStateOf(ui.form.baseUrl) }
    var token by remember { mutableStateOf(ui.form.token) }
    // Before the first registration the name field starts with the watch's own name (without "CASIO ").
    // Name and model are this watch's own (one Home Assistant device per watch), never the setup's.
    var deviceName by remember {
        mutableStateOf(if (ui.watch.registered) ui.watch.name.ifBlank { ui.form.deviceName } else ui.watch.confirmedName ?: ui.watch.suggestedName)
    }
    var model by remember { mutableStateOf(ui.watch.model) }
    val save = { events.onSave(url, token, deviceName, model) }

    // The app draws edge to edge: keep the title out of the status bar and the buttons out of the navigation bar.
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        HaTitle("Home Assistant")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HaConnectionCard(ui.saved, url, token, { url = it }, { token = it }, ui.check, ui.find, events.onFind, save)
            HaWatchCard(
                ui.saved, ui.watch, deviceName, model, { deviceName = it }, { model = it },
                changed = model.trim() != ui.watch.model, onSave = save, onRaw = events.onRawSwitch,
                onConfirm = { events.onConfirmName(deviceName, model) },
            )
            HaSentCard(ui.sent, ui.saved, events.onGroup)
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
