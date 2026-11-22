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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.io.WatchFaceCodec

private const val FACE_CHOICES = 8

/** Pick the watch face and the four items of the custom face. */
@Composable
fun WatchFaceScreen(
    onBack: () -> Unit,
    viewModel: WatchFaceViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    WatchFaceContent(ui, onBack, viewModel::refresh, viewModel::setFace, viewModel::setItem)
}

@Composable
internal fun WatchFaceContent(
    ui: WatchFaceUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFace: (Int) -> Unit,
    onItem: (Int, Int) -> Unit,
) {
    var facePicker by remember { mutableStateOf(false) }
    var slotPicker by remember { mutableStateOf<Int?>(null) }
    val face = ui.face
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Watch face", Modifier)
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    AppText(
                        text = "Pick the face and the four items of the custom face. Which slot is top right is not known yet: " +
                            "change one slot, look at the watch and note which place changed.",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    ValueRow(
                        title = "Face",
                        value = face?.let { "Index ${it.face}" } ?: "-",
                        enabled = face != null && !ui.working,
                        note = "Position in the watch's list; which index is the custom face is not known yet",
                        onClick = { facePicker = true },
                    )
                    (1..WatchFaceCodec.SLOTS).forEach { slot ->
                        ValueRow(
                            title = "Slot $slot",
                            value = face?.items?.get(slot - 1)?.let { WatchFaceCodec.itemName(it) } ?: "-",
                            enabled = face != null && !ui.working,
                            onClick = { slotPicker = slot },
                        )
                    }
                    val text = if (ui.working) "Talking to the watch..." else ui.message
                    if (text != null) AppText(text = text, modifier = Modifier.padding(12.dp))
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
            options = (0 until maxOf(FACE_CHOICES, face.face + 1)).map { "Index $it" },
            selected = face.face,
            onPick = { facePicker = false; onFace(it) },
            onDismiss = { facePicker = false },
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
