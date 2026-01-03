package org.avmedia.gshockGoogleSync

import BluetoothHelper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.PopupMessageReceiver
import org.avmedia.gshockGoogleSync.utils.CheckPermissions
import org.avmedia.gshockGoogleSync.utils.CrashReportHelper
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val application: GShockApplication
        get() = applicationContext as GShockApplication
    private lateinit var bluetoothHelper: BluetoothHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup global exception handler to catch crashes
        setupGlobalExceptionHandler()

        enableEdgeToEdge()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val requestBluetoothLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
                    ->
                    bluetoothHelper.handleBluetoothResult(result.resultCode)
                }

        bluetoothHelper =
                BluetoothHelper(
                        context = this,
                        activity = this,
                        requestBluetooth = requestBluetoothLauncher,
                        onBluetoothEnabled = {},
                        onBluetoothNotEnabled = {
                            AppSnackbar(this.getString(R.string.bluetooth_is_not_enabled))
                            Executors.newSingleThreadScheduledExecutor()
                                    .schedule({ finish() }, 3L, TimeUnit.SECONDS)
                        }
                )

        setContent {
            GShockSmartSyncTheme {
                var isInitialized by remember { mutableStateOf(false) }

                CheckPermissions {
                    LaunchedEffect(Unit) {
                        if (!isInitialized) {
                            bluetoothHelper.turnOnBLE {
                                application.init()
                                isInitialized = true
                            }
                        }
                    }

                    if (isInitialized) {
                        Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                // 2. Tell the Scaffold NOT to protect the status/nav bar areas
                                // This allows your background to flow behind them.
                                contentWindowInsets =
                                        androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                        ) { contentPadding ->
                            Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                            ) {
                                // Note: If AppContainer uses 'contentPadding', it will still
                                // push content away from edges. To take over the WHOLE screen,
                                // you can pass 'PaddingValues(0.dp)' or ignore it inside the
                                // AppContainer.
                                application.AppContainer(contentPadding)
                                PopupMessageReceiver()
                            }
                        }
                    }
                }
            }
        }
    }

    /** Setup global exception handler to catch and log all uncaught exceptions */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "Uncaught exception on thread: ${thread.name}")
                CrashReportHelper.logCrash(
                        this,
                        throwable,
                        "Uncaught exception on thread: ${thread.name}"
                )
            } catch (e: Exception) {
                // If crash logging fails, at least log to Timber
                Timber.e(e, "Failed to log crash")
            } finally {
                // Call the default handler to let the system handle the crash
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Timber.i("Global exception handler installed")
    }
}
