package org.avmedia.gshockGoogleSync.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import AppTextLarge
import org.avmedia.gshockapi.model.WatchFaceItem

private val FACE_ITEMS = listOf(
    WatchFaceItem.STEPS to "Steps",
    WatchFaceItem.HEART_RATE to "Heart rate",
    WatchFaceItem.HEART_RATE_MIN_MAX to "Heart rate min / max",
    WatchFaceItem.SLEEP to "Sleep",
    WatchFaceItem.SUNRISE_SUNSET to "Sunrise / sunset",
    WatchFaceItem.MOON to "Moon",
)

/** DW-H5600 watch face row. Tapping it opens a dialog to choose the face and the display item. */
@Composable
fun WatchFace(
    onUpdate: (SettingsViewModel.WatchFace) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.state.collectAsState()
    val setting = state.settingsMap[SettingsViewModel.WatchFace::class.java] as? SettingsViewModel.WatchFace
        ?: return
    var showDialog by remember { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextLarge(
                text = stringResource(id = R.string.watch_face),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = summary(setting),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(id = R.string.watch_face)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choice(
                        label = "Face 1 (weekday)",
                        selected = setting.face == 1,
                    ) { onUpdate(setting.copy(face = 1)) }
                    choice(
                        label = "Face 2 (customisable)",
                        selected = setting.face == 2,
                    ) { onUpdate(setting.copy(face = 2, item = setting.item ?: WatchFaceItem.STEPS)) }

                    Text(
                        text = stringResource(id = R.string.watch_face_item),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FACE_ITEMS.forEach { (item, label) ->
                        choice(label = label, selected = setting.item == item) {
                            onUpdate(setting.copy(item = item))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(id = R.string.done))
                }
            },
        )
    }
}

@Composable
private fun choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun summary(setting: SettingsViewModel.WatchFace): String =
    if (setting.face == 2) {
        "Face 2: " + (FACE_ITEMS.firstOrNull { it.first == setting.item }?.second ?: "not set")
    } else {
        "Face 1 (weekday)"
    }
