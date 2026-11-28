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
    waitingAddress: String?,
    onSelect: (PreConnectionViewModel.DeviceItem) -> Unit,
    onCancelWaiting: () -> Unit,
    onToggleAllowConnection: ((PreConnectionViewModel.DeviceItem, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            devices.forEach { device ->
                WatchCard(
                    device = device,
                    waiting = OpenOnConnect.sameAddress(waitingAddress, device.address),
                    onSelect = { onSelect(device) },
                    onCancelWaiting = onCancelWaiting,
                    onToggleAllowConnection = onToggleAllowConnection?.let { cb -> { on -> cb(device, on) } },
                )
            }
        }
    }
}

@Composable
private fun WatchCard(
    device: PreConnectionViewModel.DeviceItem,
    waiting: Boolean,
    onSelect: () -> Unit,
    onCancelWaiting: () -> Unit,
    onToggleAllowConnection: ((Boolean) -> Unit)? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(watchImageResId(device.name)),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    if (device.isAlwaysConnected && onToggleAllowConnection != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = if (device.allowConnection) "Allow Connection: ON" else "Allow Connection: OFF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = device.allowConnection,
                                onCheckedChange = onToggleAllowConnection,
                            )
                        }
                    }
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
