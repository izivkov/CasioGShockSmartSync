package org.avmedia.gshockGoogleSync.ui.others

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.session.OpenOnConnect
import org.avmedia.gshockGoogleSync.session.SessionState
import org.avmedia.gshockGoogleSync.session.WatchStatus
import org.avmedia.gshockGoogleSync.ui.common.AppCard

/** The picture of a watch by its Bluetooth name; the GW-B5600 when the model is not known. */
fun watchImageResId(watchName: String): Int = when {
    // Before the GA/GW checks: "ABL-100WE" contains neither, but keep it first so the more specific model always wins.
    "ABL" in watchName -> R.drawable.abl_100we
    "GA" in watchName || "GMA" in watchName -> R.drawable.ga_b2100
    "GW" in watchName || "GMW" in watchName -> R.drawable.gw_b5600
    "H5600" in watchName -> R.drawable.dw_h5600
    "DW" in watchName || "DMW" in watchName -> R.drawable.dw_b5600
    "ECB" in watchName -> R.drawable.ecb_30d
    "OCW-S400" in watchName -> R.drawable.ocw_s400
    else -> R.drawable.gw_b5600
}

/**
 * The watch list: one card per watch with its picture, name, live status and a menu. A tap opens the watch screens
 * when it is connected, else waits up to two minutes for it ([OpenOnConnect.waitFor]).
 */
@Composable
fun WatchCards(
    devices: List<PreConnectionViewModel.DeviceItem>,
    session: SessionState,
    keepHolder: String?,
    waitingAddress: String?,
    onSelect: (PreConnectionViewModel.DeviceItem) -> Unit,
    onOptions: (PreConnectionViewModel.DeviceItem) -> Unit,
    onCancelWaiting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            devices.forEach { device ->
                WatchCard(
                    device = device,
                    status = WatchStatus.of(device.address, session, keepHolder),
                    waiting = OpenOnConnect.sameAddress(waitingAddress, device.address),
                    onSelect = { onSelect(device) },
                    onOptions = { onOptions(device) },
                    onCancelWaiting = onCancelWaiting,
                )
            }
        }
    }
}

@Composable
private fun WatchCard(
    device: PreConnectionViewModel.DeviceItem,
    status: WatchStatus,
    waiting: Boolean,
    onSelect: () -> Unit,
    onOptions: () -> Unit,
    onCancelWaiting: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(watchImageResId(device.name)),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .alpha(if (status == WatchStatus.NOT_CONNECTED) 0.6f else 1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    WatchStatusLine(status)
                }
                IconButton(onClick = onOptions) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.watch_options))
                }
            }
            if (waiting) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.waiting_for_watch, device.name),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelWaiting) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

/** Status icon and short text: connected (accent), waiting (outline), not connected (dimmed). */
@Composable
fun WatchStatusLine(status: WatchStatus) {
    val (icon: ImageVector, text: Int) = when (status) {
        WatchStatus.CONNECTED -> Icons.Filled.BluetoothConnected to R.string.watch_status_connected
        WatchStatus.WAITING -> Icons.Outlined.Bluetooth to R.string.watch_status_waiting
        WatchStatus.NOT_CONNECTED -> Icons.Filled.BluetoothDisabled to R.string.watch_status_not_connected
    }
    val color = when (status) {
        WatchStatus.CONNECTED -> MaterialTheme.colorScheme.primary
        WatchStatus.WAITING -> MaterialTheme.colorScheme.onSurface
        WatchStatus.NOT_CONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The ⋮ sheet of one watch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchOptionsSheet(
    device: PreConnectionViewModel.DeviceItem,
    keepOn: Boolean,
    otherHolderName: String?,
    onKeepConnected: (Boolean) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
    plugins: @Composable () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WatchOptions(device, keepOn, otherHolderName, onKeepConnected, onForget, plugins)
    }
}

/**
 * "Keep connected in background" (only for watches that can stay connected; one watch at a time, with a line naming
 * the watch it is turned off for) and Forget.
 */
@Composable
fun WatchOptions(
    device: PreConnectionViewModel.DeviceItem,
    keepOn: Boolean,
    otherHolderName: String?,
    onKeepConnected: (Boolean) -> Unit,
    onForget: () -> Unit,
    /** "Plugins for this watch" (the app's plugins and how far each works with this watch). */
    plugins: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Text(device.name, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.watch_options),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (device.isAlwaysConnected) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.keep_connected_in_background), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.keep_connected_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!keepOn && otherHolderName != null) {
                        Text(
                            stringResource(R.string.keep_connected_turns_off, otherHolderName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Switch(checked = keepOn, onCheckedChange = onKeepConnected, modifier = Modifier.padding(start = 12.dp))
            }
        }
        plugins()
        TextButton(onClick = onForget, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.forget_watch), color = MaterialTheme.colorScheme.error)
        }
    }
}
