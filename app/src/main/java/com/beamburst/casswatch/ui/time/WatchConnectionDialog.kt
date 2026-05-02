package com.beamburst.casswatch.ui.time

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.beamburst.casswatch.R
import com.beamburst.casswatch.ui.common.AppButton
import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppSnackbar
import com.beamburst.casswatch.ui.others.CompanionDeviceHelper
import com.beamburst.casswatch.ui.others.PairedDeviceList
import com.beamburst.casswatch.ui.others.PreConnectionViewModel
import com.beamburst.casswatch.ui.others.PreparingPairingDialog
import com.beamburst.casswatch.utils.LocalDataStorage
import com.beamburst.casswatch.utils.Utils
import org.avmedia.gshockapi.ICDPDelegate
import timber.log.Timber

private const val PAIRING_TIMEOUT_MS = 60000L

@SuppressLint("MissingPermission")
@Composable
fun WatchConnectionDialog(
    onDismiss: () -> Unit,
    viewModel: PreConnectionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val showPreparing by viewModel.showPreparing.collectAsState()
    val timeoutJob = remember { mutableStateOf<Job?>(null) }

    val handleSuccessfulPairing = { device: BluetoothDevice ->
        timeoutJob.value?.cancel()
        val name = device.name ?: ""
        scope.launch(Dispatchers.IO) {
            LocalDataStorage.setDeviceName(context, device.address, name)
        }
        viewModel.setDevice(device.address, name)
        onDismiss()
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED != intent.action) return

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

                val bondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )
                val previousBondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )

                when {
                    device != null && bondState == BluetoothDevice.BOND_BONDED ->
                        handleSuccessfulPairing(device)

                    previousBondState == BluetoothDevice.BOND_BONDING &&
                        bondState == BluetoothDevice.BOND_NONE ->
                        timeoutJob.value?.cancel()
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val device = CompanionDeviceHelper.extractDevice(result.data)
            ?: return@rememberLauncherForActivityResult

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Timber.i("Starting OS pairing for ${device.address}")
            device.createBond()
            timeoutJob.value?.cancel()
            timeoutJob.value = scope.launch {
                delay(PAIRING_TIMEOUT_MS)
                Timber.w("Pairing timed out")
            }
        } else {
            handleSuccessfulPairing(device)
        }
    }

    PreparingPairingDialog(visible = showPreparing, ptrConnectionViewModel = viewModel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.watches)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (pairedDevices.isEmpty()) {
                    AppText(stringResource(R.string.no_paired_watches))
                } else {
                    PairedDeviceList(
                        devices = pairedDevices,
                        onSelect = { device ->
                            viewModel.setDevice(
                                Utils.sanitizeMacAddress(device.address),
                                device.name
                            )
                            onDismiss()
                        },
                        onDisassociate = { device ->
                            viewModel.disassociate(
                                context,
                                Utils.sanitizeMacAddress(device.address)
                            )
                            scope.launch(Dispatchers.IO) {
                                LocalDataStorage.removeDeviceAddress(context, device.address)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                AppText(stringResource(R.string.press_and_hold_connection_button_to_continue))
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.add_watch),
                onClick = {
                    viewModel.associateWithUi(
                        context,
                        object : ICDPDelegate {
                            override fun onChooserReady(chooserLauncher: IntentSender) {
                                launcher.launch(
                                    IntentSenderRequest.Builder(chooserLauncher).build()
                                )
                            }

                            override fun onError(error: String) {
                                Timber.i(error)
                                AppSnackbar(error)
                            }
                        }
                    )
                },
                modifier = Modifier.padding(end = 8.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                AppText(stringResource(R.string.cancel))
            }
        }
    )
}
