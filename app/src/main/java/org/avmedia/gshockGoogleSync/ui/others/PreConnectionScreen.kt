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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                ptrConnectionViewModel.setDeviceAddress(it.address)
                ptrConnectionViewModel.setDeviceName(it.name ?: "Unknown")
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
                        val (connectionSpinner, infoButton, pairButton) = createRefs()

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
                                .padding(bottom = 30.dp)
                                .constrainAs(pairButton) {
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                },
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

                // Bottom card: only as tall as its content + padding
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),           // reasonable padding here
                        verticalArrangement = Arrangement.Center
                    ) {
                        ConstraintLayout(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val (watchNamePanel, forgetButton, infoDeviceButton) = createRefs()

                            WatchName(
                                modifier = Modifier
                                    .constrainAs(watchNamePanel) {
                                        start.linkTo(parent.start)
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                    }
                                    .padding(start = 0.dp),
                                watchName = watchName
                            )

                            ForgetButton(
                                modifier = Modifier
                                    .padding(0.dp)
                                    .constrainAs(forgetButton) {
                                        end.linkTo(infoDeviceButton.start)
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                    }
                            )

                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp, start = 0.dp)
                                    .constrainAs(infoDeviceButton) {
                                        end.linkTo(parent.end)
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                    }
                            ) {
                                InfoButton(
                                    infoText = stringResource(
                                        id = R.string.connection_screen_device
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PairButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text = "Pair New Watch", color = Color.White)
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
