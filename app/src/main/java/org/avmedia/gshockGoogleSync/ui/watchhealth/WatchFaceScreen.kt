package org.avmedia.gshockGoogleSync.ui.watchhealth

import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.AppSubScreen
import org.avmedia.gshockapi.io.WatchFaceCodec

/** Pick the watch face and, on the custom face, what the top-right place shows. */
@Composable
fun WatchFaceScreen(
    onBack: () -> Unit,
    viewModel: WatchFaceViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    WatchFaceContent(
        ui, onBack, viewModel::refresh, viewModel::setFace,
        onTopRight = viewModel::setTopRight,
    )
}

@Composable
internal fun WatchFaceContent(
    ui: WatchFaceUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFace: (Int) -> Unit,
    onTopRight: (Int) -> Unit = {},
) {
    var facePicker by remember { mutableStateOf(false) }
    var topRightPicker by remember { mutableStateOf(false) }
    val face = ui.face
    val idle = face != null && !ui.working
    AppSubScreen(title = "Watch face", onBack = onBack) {
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
        }
        ButtonsRow(
            buttons = listOf(
                ButtonData("Read again", onRefresh, enabled = !ui.working),
            ),
        )
    }
    if (facePicker && face != null) {
        ChoiceDialog(
            title = "Face",
            // Only the faces this model has (DW-H5600 1 to 3, GBX-H5600 1 to 4).
            options = WatchFaceCodec.faces(ui.faceCount).map { WatchFaceCodec.faceName(it) },
            selected = face.face - WatchFaceCodec.FACES.first,
            onPick = { facePicker = false; onFace(it + WatchFaceCodec.FACES.first) },
            onDismiss = { facePicker = false },
        )
    }
    if (topRightPicker && face != null) {
        ChoiceDialog(
            title = "Top right shows",
            options = WatchFaceCodec.OFFERED_ITEMS.map { WatchFaceCodec.itemName(it) },
            selected = WatchFaceCodec.OFFERED_ITEMS.indexOf(face.items[WatchFaceCodec.TOP_RIGHT_SLOT - 1]),
            onPick = { topRightPicker = false; onTopRight(WatchFaceCodec.OFFERED_ITEMS[it]) },
            onDismiss = { topRightPicker = false },
        )
    }
}
