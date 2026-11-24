package org.avmedia.gshockGoogleSync.ui.watchhealth

import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.io.TidePointText
import org.avmedia.gshockapi.io.TideRecords
import org.avmedia.gshockapi.io.TideSettingCodec
import org.avmedia.gshockapi.io.TideStation
import org.avmedia.gshockapi.io.TideStationParser
import org.avmedia.gshockapi.io.TideStations
import org.avmedia.gshockapi.io.TideUserPointCodec

/** The height unit is a choice of two values, not an on/off switch: index 1 is feet. */
private val HEIGHT_UNITS = listOf("Metres", "Feet")

/** The short credit line under the Tide screen; it opens the full TICON-4 credit. */
internal const val TIDE_CREDIT_LINE = "Tide data: TICON-4, CC BY 4.0"

private const val CUSTOM_EXAMPLE = "name My port\\nlat 51.234\\nlon 2.927\\nutc 1\\nM2 182.0 4.25\\nS2 53.2 57.9\\nK1 5.6 -12.2\\nO1 9.4 167.3"

/** Tide: what the watch shows, three own points, and a port from open data. */
@Composable
fun TideScreen(
    onBack: () -> Unit,
    viewModel: TideViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    TideContent(ui, onBack, viewModel::refresh, viewModel::select, viewModel::setFeet, viewModel::savePoint, viewModel::sendPort)
}

@Composable
internal fun TideContent(
    ui: TideUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit,
    onFeet: (Boolean) -> Unit,
    onSavePoint: (Int, String, TideUserPointCodec.Point) -> Unit,
    onSendPort: (TideStation, TideForm) -> Unit,
    openPoint: Int? = null,
) {
    var sourcePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(openPoint) }
    var stationPicker by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf<TideStation?>(null) }
    var unitPicker by remember { mutableStateOf(false) }
    var creditOpen by remember { mutableStateOf(false) }
    val setting = ui.setting
    val idle = setting != null && !ui.working
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Tide", Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    TideSection("On the watch")
                    ValueRow("Shows", setting?.let { TideSettingCodec.sourceName(it.source) } ?: "-", idle) { sourcePicker = true }
                    ValueRow("Height unit", setting?.let { HEIGHT_UNITS[if (it.feet) 1 else 0] } ?: "-", idle) { unitPicker = true }
                    TideSection("Your own points")
                    (1..TideUserPointCodec.POINTS).forEach { n ->
                        val p = ui.points[n - 1]
                        val name = ui.names[n - 1].ifBlank { "Point $n" }
                        // A point nobody set holds the watch's factory values: those are not shown as if they were a place.
                        val set = p?.takeUnless(TideUserPointCodec::isFactory)
                        ValueRow(name, set?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "Not set", idle, note = set?.let { pointNote(it) }) { editing = n }
                    }
                    TideSection("Port from open data")
                    ValueRow("Send a port", ">", idle, note = "Belgian and nearby gauges, or your own constants") { stationPicker = true }
                    // CC BY 4.0 needs the credit: one short line here, the full text one tap away.
                    TextButton(onClick = { creditOpen = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(TIDE_CREDIT_LINE, fontSize = 12.sp)
                    }
                    val text = if (ui.working && ui.message == null) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
                }
                ButtonsRow(buttons = listOf(ButtonData("Read again", onRefresh, enabled = !ui.working), ButtonData("Back", onBack)))
            }
        }
    }
    if (sourcePicker && setting != null) {
        ChoiceDialog(
            title = "Shows",
            options = (0..4).map { TideSettingCodec.sourceName(it) },
            selected = setting.source,
            onPick = { sourcePicker = false; onSelect(it) },
            onDismiss = { sourcePicker = false },
        )
    }
    if (unitPicker && setting != null) {
        ChoiceDialog(
            title = "Height unit",
            options = HEIGHT_UNITS,
            selected = if (setting.feet) 1 else 0,
            onPick = { unitPicker = false; onFeet(it == 1) },
            onDismiss = { unitPicker = false },
        )
    }
    if (creditOpen) {
        AlertDialog(
            onDismissRequest = { creditOpen = false },
            title = { Text("Tide data") },
            text = { Text(TideStations.ATTRIBUTION, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { creditOpen = false }) { Text("Close") } },
        )
    }
    editing?.let { n ->
        PointDialog(n, ui.names[n - 1], ui.points[n - 1]?.takeUnless(TideUserPointCodec::isFactory), { editing = null }) { name, p -> editing = null; onSavePoint(n, name, p) }
    }
    if (stationPicker) {
        val all = TideStations.ALL
        ChoiceDialog(
            title = "Port",
            options = all.map { it.name } + "Custom constants...",
            selected = -1,
            onPick = { stationPicker = false; if (it < all.size) sending = all[it] else customDialog = true },
            onDismiss = { stationPicker = false },
        )
    }
    if (customDialog) CustomDialog({ customDialog = false }) { customDialog = false; sending = it }
    sending?.let { s -> SendDialog(s, { sending = null }) { form -> sending = null; onSendPort(s, form) } }
}

@Composable
private fun TideSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
}

private fun pointNote(p: TideUserPointCodec.Point): String =
    "%.2f, %.2f, UTC%+.2f%s".format(p.latitude, p.longitude, p.utcOffsetHours, if (p.summerTime) ", summer time" else "")

@Composable
private fun PointDialog(
    n: Int,
    name: String,
    point: TideUserPointCodec.Point?,
    onDismiss: () -> Unit,
    onSave: (String, TideUserPointCodec.Point) -> Unit,
) {
    val zone = java.util.TimeZone.getDefault()
    var nameText by remember { mutableStateOf(name) }
    var time by remember { mutableStateOf(point?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "12:00") }
    var lat by remember { mutableStateOf(point?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(point?.longitude?.toString() ?: "") }
    var utc by remember { mutableStateOf((point?.utcOffsetHours ?: (zone.rawOffset / 3_600_000f)).toString()) }
    var summer by remember { mutableStateOf(point?.summerTime ?: false) }
    val parsed = TidePointText.parse(time, lat, lon, utc, summer)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Own point $n") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Field("Name (kept in the app only)", nameText) { nameText = it }
                Field("High tide time (hh:mm)", time) { time = it }
                Field("Latitude (degrees, north +)", lat) { lat = it }
                Field("Longitude (degrees, east +)", lon) { lon = it }
                Field("UTC offset (hours)", utc) { utc = it }
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Summer time", modifier = Modifier.weight(1f))
                    Switch(checked = summer, onCheckedChange = { summer = it })
                }
                if (parsed == null) Text("Check the values.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let { onSave(nameText, it) } }, enabled = parsed != null) { Text("Save and show") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
}

@Composable
private fun CustomDialog(onDismiss: () -> Unit, onStation: (TideStation) -> Unit) {
    var text by remember { mutableStateOf(CUSTOM_EXAMPLE.replace("\\n", "\n")) }
    val result = TideStationParser.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom constants") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("One item per line: name, lat, lon, utc (hours), optional z0 (cm), then a constituent with its amplitude (cm) and Greenwich phase lag (degrees).", fontSize = 12.sp)
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                when (result) {
                    is TideStationParser.Result.Error -> Text(result.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    is TideStationParser.Result.Ok -> if (result.ignored.isNotEmpty()) Text("Not used by the watch: ${result.ignored.joinToString()}", fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { (result as? TideStationParser.Result.Ok)?.let { onStation(it.station) } }, enabled = result is TideStationParser.Result.Ok) { Text("Next") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SendDialog(station: TideStation, onDismiss: () -> Unit, onForm: (TideForm) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send ${station.name}") },
        text = {
            Column {
                Text("The constants go to the watch, which computes the tides itself. Tide times are in standard time (no summer time rule).", fontSize = 13.sp)
                if (TideRecords.z0Approximate(station)) Text("Mean level: approximate (sum of the main amplitudes). Times are not affected, heights are.", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                if (!TideRecords.can60(station)) Text("Too few constants for the 60 form.", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onForm(TideForm.FOUR) }, enabled = TideRecords.can4(station)) { Text("Send (4 constituents)") } },
        dismissButton = {
            Column {
                TextButton(onClick = { onForm(TideForm.SIXTY) }, enabled = TideRecords.can60(station)) { Text("Send (60, untested)") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
