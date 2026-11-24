package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watch.AppStores
import AppSwitch
import AppText
import AppTextLarge
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppIconFromResource
import org.avmedia.gshockGoogleSync.ui.common.HorizontalSeparator

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

/** The first entry of the Settings tab: Home Assistant with its status, opening the Home Assistant screen. */
@Composable
fun HaSettingsEntry(onOpen: () -> Unit) {
    val context = LocalContext.current
    val status = remember { HaStatus.of(AppStores.haStore(context), org.avmedia.gshockGoogleSync.watch.CurrentWatch.keyOrLast(context)) }
    HaSettingsEntryContent(status, onOpen)
}

@Composable
internal fun HaSettingsEntryContent(status: String, onOpen: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconFromResource(resourceId = R.drawable.ic_home_assistant)
            HorizontalSeparator(width = 8.dp)
            Column(modifier = Modifier.weight(1f)) {
                AppTextLarge(text = stringResource(R.string.home_assistant))
                AppText(text = status, fontSize = 12.sp)
            }
            AppTextLarge(text = ">")
        }
    }
}

/**
 * The Home Assistant row of the Actions tab: on or off, and what a press of the watch button runs there. Nothing
 * about the connection: that is in Settings > Home Assistant, which [onOpenSetup] opens when it is not set up yet.
 */
@Composable
fun HaButtonActionRow(onOpenSetup: () -> Unit, viewModel: HaViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val toggles = remember { AppStores.toggles(context) }
    var isOn by remember { mutableStateOf(toggles.isOn(ServiceToggles.Service.HOME_ASSISTANT)) }
    HaButtonActionContent(
        saved = ui.saved,
        isOn = isOn,
        onToggle = { isOn = it; toggles.set(ServiceToggles.Service.HOME_ASSISTANT, it) },
        actions = ui.actions,
        onChoose = viewModel::choose,
        onRefresh = viewModel::refreshActions,
        onOpenSetup = onOpenSetup,
    )
}

internal const val HA_SET_UP_LINK = "Set up in Settings > Home Assistant"

/**
 * The Home Assistant row of the Actions tab, like its neighbours (icon, name, switch, same height and padding). When
 * on, one second line in the same card: "Watch button: <friendly name>" with a chevron that opens the picker (search
 * and Refresh inside it), or, before Home Assistant is set up, the way to Settings > Home Assistant.
 */
@Composable
internal fun HaButtonActionContent(
    saved: Boolean,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    actions: HaActionsUi,
    onChoose: (HaAction?) -> Unit,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
    pickerOpen: Boolean = false,
) {
    var picker by remember { mutableStateOf(pickerOpen) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconFromResource(resourceId = R.drawable.ic_home_assistant)
                HorizontalSeparator(width = 4.dp)
                AppTextLarge(text = stringResource(R.string.home_assistant), modifier = Modifier.weight(1f))
                AppSwitch(checked = isOn, onCheckedChange = onToggle)
            }
            if (isOn) {
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
        }
    }
    if (picker) HaActionPickerDialog(actions, onChoose, onRefresh) { picker = false }
}
