package org.avmedia.gshockGoogleSync.ui.others

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppConnectionSpinner
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockapi.ICDPDelegate
import timber.log.Timber

@SuppressLint("MissingPermission")
@Composable
fun PreConnectionScreen(
    ptrConnectionViewModel: PreConnectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val watchName by ptrConnectionViewModel.watchName.collectAsState()
    val triggerPairing by ptrConnectionViewModel.triggerPairing.collectAsState()
    val pairedDevices by ptrConnectionViewModel.pairedDevices.collectAsState()

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
                ptrConnectionViewModel.setDevice(it.address, it.name ?: "Unknown")
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
                                ptrConnectionViewModel.selectDevice(device)
                            },
                            modifier = Modifier
                                .padding(end = 30.dp, bottom = 20.dp)
                                .constrainAs(deviceList) {
                                    bottom.linkTo(pairButton.top)
                                    end.linkTo(parent.end)
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
                                ptrConnectionViewModel.associate(context, object : ICDPDelegate {
                                    override fun onChooserReady(chooserLauncher: IntentSender) {
                                        launcher.launch(
                                            IntentSenderRequest.Builder(chooserLauncher).build()
                                        )
                                    }

                                    override fun onError(error: String) {
                                        Timber.e("Manual pairing error: $error")
                                    }
                                })
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        devices.take(5).forEach { device ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelect(device) }
            ) {
                if (device.isLastUsed) {
                    Text(
                        text = "→",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        if (devices.size > 5) {
            Text(
                text = "...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
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
            text = "Add Watch",
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
                contentDescription = "Add Watch",
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

@Preview(showBackground = true)
@Composable
fun PreviewConnectionScreen() {
    PreConnectionScreen()
}
