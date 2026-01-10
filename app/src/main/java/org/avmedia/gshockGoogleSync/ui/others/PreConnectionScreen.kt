package org.avmedia.gshockGoogleSync.ui.others

import AppText
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppConnectionSpinner
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.ICDPDelegate
import timber.log.Timber

// Constants for Bluetooth and Timeout logic
private const val BOND_STATE_NONE = BluetoothDevice.BOND_NONE
private const val BOND_STATE_BONDING = BluetoothDevice.BOND_BONDING
private const val BOND_STATE_BONDED = BluetoothDevice.BOND_BONDED
private const val PAIRING_TIMEOUT_MS = 60000L
private const val GUIDELINE_LIST_TOP = 0.82f

@SuppressLint("MissingPermission")
@Composable
fun PreConnectionScreen(
    ptrConnectionViewModel: PreConnectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watchName by ptrConnectionViewModel.watchName.collectAsState()
    val triggerPairing by ptrConnectionViewModel.triggerPairing.collectAsState()
    val pairedDevices by ptrConnectionViewModel.pairedDevices.collectAsState()
    val showPreparing by ptrConnectionViewModel.showPreparing.collectAsState()

    val timeoutJob = remember { mutableStateOf<Job?>(null) }

    val handleSuccessfulPairing = { device: BluetoothDevice ->
        timeoutJob.value?.cancel()
        val deviceLog: Map<String, String> = CompanionDeviceHelper.createDeviceLog(device)
        Timber.i("Pairing success detected: $deviceLog")

        val name = device.name ?: ""
        scope.launch(Dispatchers.IO) {
            LocalDataStorage.setDeviceName(context, device.address, name)
        }
        ptrConnectionViewModel.setDevice(device.address, name)
    }

    // BroadcastReceiver for ROMs like IodeOS where CDM intent returns null data
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == intent.action) {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }

                        val bondState =
                            intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BOND_STATE_NONE
                            )
                        val prevBondState =
                            intent.getIntExtra(
                                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                BOND_STATE_NONE
                            )

                        if (device != null) {
                            when (bondState) {
                                BOND_STATE_BONDED -> handleSuccessfulPairing(device)
                                BOND_STATE_NONE ->
                                    if (prevBondState == BOND_STATE_BONDING) {
                                        timeoutJob.value?.cancel()
                                    }
                            }
                        }
                    }
                }
            }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
            timeoutJob.value?.cancel()
        }
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                timeoutJob.value?.cancel()
                timeoutJob.value =
                    scope.launch {
                        delay(PAIRING_TIMEOUT_MS)
                        AppSnackbar("Pairing timed out: No bond state change detected.")
                    }

                // Utilize the external helper for extraction and fallback
                var device: BluetoothDevice? = CompanionDeviceHelper.extractDevice(result.data)

                if (device == null) {
                    Timber.i("Intent empty, checking association fallback...")
                    val associations = CompanionDeviceHelper.getAssociationsFallback(context)
                    associations.lastOrNull()?.get("address")?.let { address ->
                        val adapter = BluetoothAdapter.getDefaultAdapter()
                        device = adapter.getRemoteDevice(address)
                    }
                }

                device?.let { handleSuccessfulPairing(it) }
                    ?: Timber.i("Waiting for BroadcastReceiver...")
            }
        }

    PreparingPairingDialog(visible = showPreparing, ptrConnectionViewModel = ptrConnectionViewModel)

    LaunchedEffect(triggerPairing) {
        if (triggerPairing) {
            ptrConnectionViewModel.associate(
                context,
                object : ICDPDelegate {
                    override fun onChooserReady(chooserLauncher: IntentSender) {
                        launcher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                        ptrConnectionViewModel.onPairingTriggered()
                    }

                    override fun onError(error: String) {
                        AppSnackbar("Auto-pairing error: $error")
                        ptrConnectionViewModel.onPairingTriggered()
                    }
                }
            )
        }
    }

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()) {
                AppCard(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()) {
                    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                        val (connectionSpinner, infoButton, pairButton, deviceList) = createRefs()

                        WatchScreen(
                            imageResId =
                                when {
                                    "GA" in watchName || "GMA" in watchName ->
                                        R.drawable.ga_b2100

                                    "GW" in watchName || "GMW" in watchName ->
                                        R.drawable.gw_b5600

                                    "DW-H5600" in watchName -> R.drawable.dw_h5600
                                    "DW" in watchName || "DMW" in watchName ->
                                        R.drawable.dw_b5600

                                    "ECB" in watchName -> R.drawable.ecb_30d
                                    else -> R.drawable.gw_b5600
                                },
                            isAlwaysConnected = "DW-H5600" in watchName || "ECB" in watchName
                        )

                        AppConnectionSpinner(
                            modifier =
                                Modifier.constrainAs(connectionSpinner) { centerTo(parent) }
                        )

                        val topGuideline = createGuidelineFromTop(GUIDELINE_LIST_TOP)

                        AppCard(
                            modifier =
                                Modifier
                                    .padding(start = 16.dp, bottom = 16.dp)
                                    .constrainAs(
                                        deviceList
                                    ) {
                                        bottom.linkTo(parent.bottom)
                                        start.linkTo(parent.start)
                                        top.linkTo(topGuideline)
                                        width = Dimension.percent(0.5f)
                                        height = Dimension.fillToConstraints
                                    }
                        ) {
                            PairedDeviceList(
                                devices = pairedDevices,
                                onSelect = { device ->  // ← IMPLEMENT THIS
                                    ptrConnectionViewModel.setDevice(
                                        Utils.sanitizeMacAddress(device.address),
                                        device.name
                                    )
                                },
                                onDisassociate = { device ->
                                    ptrConnectionViewModel.disassociate(
                                        context,
                                        Utils.sanitizeMacAddress(device.address)
                                    )
                                    scope.launch(Dispatchers.IO) {
                                        LocalDataStorage.removeDeviceAddress(
                                            context,
                                            device.address
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                            )
                        }

                        Box(
                            modifier =
                                Modifier
                                    .padding(top = 30.dp, end = 30.dp)
                                    .constrainAs(
                                        infoButton
                                    ) {
                                        top.linkTo(parent.top)
                                        end.linkTo(parent.end)
                                    }
                        ) {
                            InfoButton(
                                infoText =
                                    stringResource(R.string.connection_screen_info) +
                                            " v" +
                                            BuildConfig.VERSION_NAME
                            )
                        }

                        PairButton(
                            modifier =
                                Modifier
                                    .padding(bottom = 30.dp, end = 30.dp)
                                    .constrainAs(
                                        pairButton
                                    ) {
                                        bottom.linkTo(parent.bottom)
                                        end.linkTo(parent.end)
                                    },
                            isFlashing = pairedDevices.isEmpty(),
                            onClick = {
                                ptrConnectionViewModel.associateWithUi(
                                    context,
                                    object : ICDPDelegate {
                                        override fun onChooserReady(
                                            chooserLauncher: IntentSender
                                        ) {
                                            launcher.launch(
                                                IntentSenderRequest.Builder(
                                                    chooserLauncher
                                                )
                                                    .build()
                                            )
                                        }

                                        override fun onError(error: String) {
                                            Timber.i(error)
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

// --- Composable Components ---

@Composable
fun PairedDeviceList(
    devices: List<PreConnectionViewModel.DeviceItem>,
    onSelect: (PreConnectionViewModel.DeviceItem) -> Unit,
    onDisassociate: (PreConnectionViewModel.DeviceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier
        .fillMaxHeight()
        .verticalScroll(scrollState)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoveButton(onClick = { onDisassociate(device) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .clickable { onSelect(device) }
                            .weight(1f)
                    )
                    if (device.isLastUsed) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Selected",
                            tint = Color.Red,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer(scaleX = -1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(18.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .background(Color.Black, CircleShape)
                .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Remove, "Remove", modifier = Modifier.size(12.dp), tint = Color.White) }
}

@Composable
fun PairButton(modifier: Modifier = Modifier, onClick: () -> Unit, isFlashing: Boolean = false) {
    val transition = rememberInfiniteTransition("flashing")
    val alpha by
    transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec =
            infiniteRepeatable(
                tween(1000, easing = LinearEasing),
                RepeatMode.Reverse
            ),
        label = "alpha"
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppText(
            Utils.wrapString(stringResource(R.string.add_watch), 10),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Button(
            onClick = onClick,
            modifier =
                Modifier
                    .size(56.dp)
                    .alpha(if (isFlashing) alpha else 1f)
                    .border(1.dp, Color.White, CircleShape),
            shape = CircleShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
            contentPadding = PaddingValues(0.dp)
        ) { Icon(Icons.Default.Add, "Add", modifier = Modifier.size(32.dp)) }
    }
}

@Composable
fun WatchScreen(imageResId: Int, isAlwaysConnected: Boolean) {
    Box {
        if (isAlwaysConnected) {
            WatchImageWithOverlayAlwaysConnected(Modifier, imageResId)
        } else {
            WatchImageWithOverlay(Modifier, imageResId, 0.55f)
        }
    }
}

@Composable
fun PreparingPairingDialog(visible: Boolean, ptrConnectionViewModel: PreConnectionViewModel) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = { ptrConnectionViewModel.hidePreparing() },
        title = { AppText(stringResource(R.string.looking_for_a_device)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
