package org.avmedia.smartsync.homeassistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The status the Settings entry shows: set up or not, and whether the last send worked. */
object HaStatus {
    const val NOT_SET_UP = "Not set up"
    const val CONNECTED = "Connected"
    const val FAILED = "Last send failed"

    fun text(saved: Boolean, lastOk: Boolean?): String = when {
        !saved -> NOT_SET_UP
        lastOk == false -> FAILED
        else -> CONNECTED
    }

    /** The status of [watch]'s last send (the connected or last watch). */
    fun of(store: HaStore, watch: String): String = text(store.load() != null, store.lastResultOk(watch))
}

/**
 * The Home Assistant part of its Actions card (the app draws the icon, the name and the on/off switch above it): what
 * a press of the watch button runs there. Nothing about the connection: that is the Home Assistant screen, which
 * [onOpenSetup] opens when it is not set up yet.
 */
@Composable
fun HaButtonActionRow(onOpenSetup: () -> Unit, viewModel: HaViewModel) {
    val ui by viewModel.ui.collectAsState()
    HaButtonActionContent(
        saved = ui.saved,
        actions = ui.actions,
        onChoose = viewModel::choose,
        onRefresh = viewModel::refreshActions,
        onOpenSetup = onOpenSetup,
    )
}

internal const val HA_SET_UP_LINK = "Set up in Settings > Home Assistant"

/**
 * The second line of the Home Assistant card in the Actions tab while it is on: "Watch button: <friendly name>" with a
 * chevron that opens the picker (search and Refresh inside it), or, before Home Assistant is set up, the way to its screen.
 */
@Composable
internal fun HaButtonActionContent(
    saved: Boolean,
    actions: HaActionsUi,
    onChoose: (HaAction?) -> Unit,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
    pickerOpen: Boolean = false,
) {
    var picker by remember { mutableStateOf(pickerOpen) }
    Column(modifier = Modifier.fillMaxWidth()) {
        val text = if (saved) "Watch button: ${buttonActionName(actions.chosen)}" else HA_SET_UP_LINK
        val open: () -> Unit = if (saved) ({ picker = true }) else onOpenSetup
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = open).padding(start = 40.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (saved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (saved && actions.error != null && !picker) {
            AppText(text = actions.error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 40.dp, end = 12.dp, bottom = 8.dp))
        }
    }
    if (picker) HaActionPickerDialog(actions, onChoose, onRefresh) { picker = false }
}
