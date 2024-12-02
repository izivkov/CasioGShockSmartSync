package org.avmedia.gshockGoogleSync

import BluetoothHelper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.SnackbarController
import org.avmedia.gshockGoogleSync.utils.CheckPermissions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val application: GShockApplication
        get() = applicationContext as GShockApplication
    private lateinit var bluetoothHelper: BluetoothHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val requestBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                bluetoothHelper.handleBluetoothResult(result.resultCode)
            }

        bluetoothHelper = BluetoothHelper(
            context = this,
            activity = this,
            requestBluetooth = requestBluetoothLauncher,
            onBluetoothEnabled = { },
            onBluetoothNotEnabled = {
                AppSnackbar(getString(R.string.bluetooth_is_not_enabled))
                Executors.newSingleThreadScheduledExecutor()
                    .schedule({ finish() }, 3L, TimeUnit.SECONDS)
            }
        )

        setContent {
            CheckPermissions {
                bluetoothHelper.turnOnBLE {
                    setContent {
                        GShockSmartSyncTheme {
                            SnackbarController.snackbarHostState = remember { SnackbarHostState() }
                            Scaffold(
                                snackbarHost = {
                                    SnackbarController.snackbarHostState?.let { nonNullHostState ->
                                        SnackbarHost(hostState = nonNullHostState)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                            ) { contentPadding ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(contentPadding),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    setContent {
                                        application.Run()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


