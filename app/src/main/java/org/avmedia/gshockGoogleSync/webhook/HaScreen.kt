package org.avmedia.gshockGoogleSync.webhook

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
        ),
    )
}

/** The stateless body of [HaScreen]: state in, events out (also used by the screenshot tests). */
@Composable
internal fun HaScreenContent(ui: HaUi, events: HaEvents = HaEvents(), pickerOpen: Boolean = false) {
    var url by remember { mutableStateOf(ui.form.baseUrl) }
    var token by remember { mutableStateOf(ui.form.token) }
    var deviceName by remember { mutableStateOf(ui.form.deviceName) }
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
            )
            HaSentCard(ui.sent, ui.saved, events.onGroup)
            HaButtonCard(ui.saved, ui.actions, events.onChoose, events.onRefreshActions, pickerOpen)
        }
        ButtonsRow(
            buttons = listOf(
                ButtonData("Back", events.onBack),
                ButtonData("Forget", { events.onForget(); url = ""; token = ""; deviceName = ""; model = "" }, enabled = ui.saved),
            )
        )
    }
}
