package org.avmedia.gshockGoogleSync.ui.others

import AppText
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppConnectionSpinner
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ICDPDelegate
import timber.log.Timber

@SuppressLint("MissingPermission")
@Composable
fun PreConnectionScreen(
    ptrConnectionViewModel: PreConnectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val watchName by ptrConnectionViewModel.watchName.collectAsState()
    val triggerPairing by ptrConnectionViewModel.triggerPairing.collectAsState()
    val pairedDevices by ptrConnectionViewModel.pairedDevices.collectAsState()
    val showPreparing by ptrConnectionViewModel.showPreparing.collectAsState()

    // PreparingPairingDialog(visible = showPreparing)
    PreparingPairingDialog(
        visible = showPreparing,
        ptrConnectionViewModel = ptrConnectionViewModel
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    println("Result data: ${result.data}")
                    result.data?.getParcelableExtra(
                        CompanionDeviceManager.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                }

            device?.let {
                val name = if (it.name.isNullOrBlank()) "G-SHOCK" else it.name

                CoroutineScope(Dispatchers.IO).launch {
                    LocalDataStorage.setDeviceName(context, it.address, name)
                }
                ptrConnectionViewModel.setDevice(it.address, name)
            }
        }
    }

    LaunchedEffect(triggerPairing) {
        if (triggerPairing) {
            ptrConnectionViewModel.associate(context, object : ICDPDelegate {
                override fun onChooserReady(chooserLauncher: IntentSender) {
                    launcher.launch(
                        IntentSenderRequest.Builder(chooserLauncher).build()
                    )
                    ptrConnectionViewModel.onPairingTriggered()
                }

                override fun onError(error: String) {
                    Timber.e("Auto-pairing error: $error")
                    ptrConnectionViewModel.onPairingTriggered()
                }
            })
        }
    }

    val getImageId: (String) -> Int = { name ->
        when {
            "GA" in name || "GMA" in name -> R.drawable.ga_b2100
            "GW" in name || "GMW" in name -> R.drawable.gw_b5600
            "DW-H5600" in name -> R.drawable.dw_h5600
            "DW" in name || "DMW" in name -> R.drawable.dw_b5600
            "ECB" in name -> R.drawable.ecb_30d
            else -> R.drawable.gw_b5600
        }
    }

    val isAlwaysConnected: (String) -> Boolean = { name ->
        when {
            "DW-H5600" in name || "ECB" in name -> true
            else -> false
        }
    }

    val getArrowsVerticalPosition: (String) -> Float = { _ -> 0.55f }

    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top card fills the remaining space
                AppCard(
                    modifier = Modifier
                        .weight(1f)        // <- take all remaining height
                        .fillMaxWidth()
                ) {
                    ConstraintLayout(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val (connectionSpinner, infoButton, pairButton, deviceList) = createRefs()

                        WatchScreen(
                            imageResId = getImageId(watchName),
                            isAlwaysConnected = isAlwaysConnected(watchName),
                            arrowsVerticalPosition = getArrowsVerticalPosition(watchName),
                        )

                        AppConnectionSpinner(
                            modifier = Modifier.constrainAs(connectionSpinner) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            }
                        )

                        PairedDeviceList(
                            devices = pairedDevices,
                            onSelect = { device ->
                                // Uncomment the following line if you want to select a device
                                // ptrConnectionViewModel.selectDevice(device)
                            },
                            onDisassociate = { device ->
                                ptrConnectionViewModel.disassociate(context, device.address)
                                val scope = CoroutineScope(Dispatchers.IO)
                                scope.launch {
                                    LocalDataStorage.removeDeviceAddress(context, device.address)
                                }
                            },
                            modifier = Modifier
                                .padding(start = 10.dp, bottom = 40.dp)
                                .constrainAs(deviceList) {
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                }
                        )

                        Box(
                            modifier = Modifier
                                .padding(top = 30.dp, end = 30.dp)
                                .constrainAs(infoButton) {
                                    top.linkTo(parent.top)
                                    end.linkTo(parent.end)
                                }
                        ) {
                            InfoButton(
                                infoText = stringResource(
                                    id = R.string.connection_screen_info
                                ) + " v" + BuildConfig.VERSION_NAME
                            )
                        }

                        PairButton(
                            modifier = Modifier
                                .padding(bottom = 30.dp, end = 30.dp)
                                .constrainAs(pairButton) {
                                    bottom.linkTo(parent.bottom)
                                    end.linkTo(parent.end)
                                },
                            isFlashing = pairedDevices.isEmpty(),
                            onClick = {
                                ptrConnectionViewModel.associateWithUi(
                                    context,
                                    object : ICDPDelegate {

                                        override fun onChooserReady(chooserLauncher: IntentSender) {
                                            launcher.launch(
                                                IntentSenderRequest.Builder(chooserLauncher).build()
                                            )
                                        }

                                        override fun onError(error: String) {
                                            Timber.e(error)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PairedDeviceList(
    devices: List<PreConnectionViewModel.DeviceItem>,
    onSelect: (PreConnectionViewModel.DeviceItem) -> Unit,
    onDisassociate: (PreConnectionViewModel.DeviceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .wrapContentSize(Alignment.CenterStart) // Changed from CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.Start, // Changed from End
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            devices.take(5).forEach { device ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start // Force alignment to the left
                ) {
                    // 1. Remove Button (Far Left)
                    RemoveButton(onClick = { onDisassociate(device) })

                    Spacer(modifier = Modifier.width(12.dp))

                    // 2. Watch Name (Middle)
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable { onSelect(device) }
                            .padding(vertical = 4.dp)
                    )

                    // 3. Selection Indicator (Right of text)
                    if (device.isLastUsed) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Selected",
                            tint = Color.Red,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 8.dp) // Space between text and arrow
                                .graphicsLayer(scaleX = -1f) // This flips the triangle to point left
                        )
                    }
                }
            }
            if (devices.size > 5) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 40.dp) // Padding from the left
                )
            }
        }
    }
}

@Composable
fun RemoveButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 12.dp)
            .size(18.dp)
            .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .background(Color.Black, CircleShape)
            .clickable(
                onClick = onClick,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Remove,
            contentDescription = "Remove Watch",
            modifier = Modifier.size(12.dp),
            tint = Color.White
        )
    }
}

@Composable
fun PairButton(modifier: Modifier = Modifier, onClick: () -> Unit, isFlashing: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "flashing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val currentAlpha = if (isFlashing) alpha else 1f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.add_watch),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Button(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .alpha(currentAlpha)
                .border(1.dp, Color.White, CircleShape),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.add_watch),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun WatchScreen(
    modifier: Modifier = Modifier,
    imageResId: Int = R.drawable.gw_b5600,
    arrowsVerticalPosition: Float = 0.55f,
    isAlwaysConnected: Boolean = false
) {
    Box(modifier = modifier) {
        if (isAlwaysConnected) {
            WatchImageWithOverlayAlwaysConnected(
                modifier,
                imageResId
            )
        } else {
            WatchImageWithOverlay(
                modifier,
                imageResId,
                arrowsVerticalPosition
            )
        }
    }
}

@Composable
fun PreparingPairingDialog(
    visible: Boolean,
    ptrConnectionViewModel: PreConnectionViewModel = viewModel(),
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = { ptrConnectionViewModel.hidePreparing() },
        title = { AppText(stringResource(R.string.looking_for_a_device)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                AppText(stringResource(R.string.press_and_hold_connection_button_to_continue))
            }
        },
        confirmButton = {},
        dismissButton = {
            AppButton(
                onClick = { ptrConnectionViewModel.hidePreparing() },
                text = stringResource(android.R.string.cancel)
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConnectionScreen() {
    PreConnectionScreen()
}
