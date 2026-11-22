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
import org.avmedia.gshockapi.io.WatchFaceCodec

private const val RAW_FACE_CHOICES = 8

/** Pick the watch face and, on the custom face, what the top-right place shows. */
@Composable
fun WatchFaceScreen(
    onBack: () -> Unit,
    viewModel: WatchFaceViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    WatchFaceContent(
        ui, onBack, viewModel::refresh, viewModel::setFace, viewModel::setItem,
        onTopRight = viewModel::setTopRight, onRawFace = viewModel::setFaceRaw,
    )
}

@Composable
internal fun WatchFaceContent(
    ui: WatchFaceUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFace: (Int) -> Unit,
    onItem: (Int, Int) -> Unit,
    onTopRight: (Int) -> Unit = {},
    onRawFace: (Int) -> Unit = {},
    detailsOpen: Boolean = false,
) {
    var facePicker by remember { mutableStateOf(false) }
    var topRightPicker by remember { mutableStateOf(false) }
    var rawFacePicker by remember { mutableStateOf(false) }
    var slotPicker by remember { mutableStateOf<Int?>(null) }
    var details by remember { mutableStateOf(detailsOpen) }
    val face = ui.face
    val idle = face != null && !ui.working
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Watch face", Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    ValueRow(
                        title = "Face",
                        value = face?.let { WatchFaceCodec.faceName(it.face) } ?: "-",
                        enabled = idle,
                        onClick = { facePicker = true },
                    )
                    if (face?.face == WatchFaceCodec.CUSTOM_FACE) {
                        ValueRow(
                            title = "Top right shows",
                            value = WatchFaceCodec.itemName(face.items[WatchFaceCodec.TOP_RIGHT_SLOT - 1]),
                            enabled = idle,
                            note = "The other places of the custom face cannot be changed.",
                            onClick = { topRightPicker = true },
                        )
                    }
                    val text = if (ui.working) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details") }
                    if (details) {
                        AppText(
                            text = "For decoding only. Face index 0 does nothing; on the custom face only slot 3 (top right) has an effect.",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        ValueRow(
                            title = "Face index",
                            value = face?.let { "Index ${it.face}" } ?: "-",
                            enabled = idle,
                            onClick = { rawFacePicker = true },
                        )
                        (1..WatchFaceCodec.SLOTS).forEach { slot ->
                            ValueRow(
                                title = "Slot $slot",
                                value = face?.items?.get(slot - 1)?.let { WatchFaceCodec.itemName(it) } ?: "-",
                                enabled = idle,
                                onClick = { slotPicker = slot },
                            )
                        }
                    }
                }
                ButtonsRow(
                    buttons = listOf(
                        ButtonData("Read again", onRefresh, enabled = !ui.working),
                        ButtonData("Back", onBack),
                    ),
                )
            }
        }
    }
    if (facePicker && face != null) {
        ChoiceDialog(
            title = "Face",
            options = WatchFaceCodec.FACES.map { WatchFaceCodec.faceName(it) },
            selected = face.face - WatchFaceCodec.FACES.first,
            onPick = { facePicker = false; onFace(it + WatchFaceCodec.FACES.first) },
            onDismiss = { facePicker = false },
        )
    }
    if (topRightPicker && face != null) {
        ChoiceDialog(
            title = "Top right shows",
            options = WatchFaceCodec.ITEM_NAMES,
            selected = face.items[WatchFaceCodec.TOP_RIGHT_SLOT - 1],
            onPick = { topRightPicker = false; onTopRight(it) },
            onDismiss = { topRightPicker = false },
        )
    }
    if (rawFacePicker && face != null) {
        ChoiceDialog(
            title = "Face index",
            options = (0 until maxOf(RAW_FACE_CHOICES, face.face + 1)).map { "Index $it" },
            selected = face.face,
            onPick = { rawFacePicker = false; onRawFace(it) },
            onDismiss = { rawFacePicker = false },
        )
    }
    val slot = slotPicker
    if (slot != null && face != null) {
        ChoiceDialog(
            title = "Slot $slot",
            options = WatchFaceCodec.ITEM_NAMES,
            selected = face.items[slot - 1],
            onPick = { slotPicker = null; onItem(slot, it) },
            onDismiss = { slotPicker = null },
        )
    }
}
