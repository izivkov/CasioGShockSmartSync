package org.avmedia.gShockPhoneSync

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.services.DeviceManager
import org.avmedia.gShockPhoneSync.services.InactivityWatcher
import org.avmedia.gShockPhoneSync.theme.GShockSmartSyncTheme
import org.avmedia.gShockPhoneSync.ui.common.AppSnackbar
import org.avmedia.gShockPhoneSync.ui.common.PopupMessageReceiver
import org.avmedia.gShockPhoneSync.ui.common.SnackbarController
import org.avmedia.gShockPhoneSync.ui.others.PreConnectionScreen
import org.avmedia.gShockPhoneSync.ui.others.RunActionsScreen
import org.avmedia.gShockPhoneSync.utils.BluetoothHelper
import org.avmedia.gShockPhoneSync.utils.CheckPermissions
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    // private val api = GShockAPIMock(this)
    private val api = GShockAPI(this)

    private var deviceManager: DeviceManager
    private lateinit var bluetoothHelper: BluetoothHelper

    init {
        instance = this

        // do not delete this. DeviceManager needs to be running to save the last device name to reuse on next start.
        deviceManager = DeviceManager
    }

    class MainViewModel(application: Application) : AndroidViewModel(application) {

        /*
        If the app resets due to external factors, handle it here.
         */
        @Composable
        fun HandleReset() {
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {

                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                        coroutineScope.launch {
                            api().close()
                        }
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { /* Cleanup */ }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CheckPermissions {

                val mainViewModel: MainViewModel = viewModel()
                mainViewModel.HandleReset()

                checkIfBluetoothEnabled()
                createAppEventsSubscription()

                GShockSmartSyncTheme {
                    SnackbarController.snackbarHostState = remember { SnackbarHostState() }

                    Scaffold(
                        snackbarHost = {
                            SnackbarController.snackbarHostState?.let { nonNullHostState ->
                                SnackbarHost(hostState = nonNullHostState)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { contentPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            setContent {
                                RunWithChecks()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Run() {
        StartScreen { PreConnectionScreen() }

        // Ensure a new LaunchedEffect is triggered each time Run() is called by using a unique key
        LaunchedEffect(key1 = System.currentTimeMillis()) {
            waitForConnectionCached()
        }
    }

    @Composable
    private fun RunWithChecks() {
        // Do some checks here

        Run()
    }

    /*
    Start all screens through this function
     */
    @Composable
    fun StartScreen(content: @Composable () -> Unit) {

        GShockSmartSyncTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            InactivityWatcher.resetTimer(this@MainActivity)
                        }
                    },  // Use full screen size for consistency
                color = MaterialTheme.colorScheme.background
            ) {
                content()
                PopupMessageReceiver()
            }
        }
    }

    private fun createAppEventsSubscription() {

        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                // if this watch is always connected, like the ECB-30D, set time here
                // Auto-time adjustment will not happen
                if (!WatchInfo.alwaysConnected) {
                    InactivityWatcher.start(this@MainActivity)
                }
            },

            EventAction("WatchInitializationCompleted") {
                setContent {
                    StartScreen {
                        if (api().isActionButtonPressed() || api().isAutoTimeStarted() || api().isFindPhoneButtonPressed()) {
                            RunActionsScreen()
                        } else {
                            BottomNavigationBarWithPermissions()
                        }
                    }
                }
            },
            EventAction("ConnectionFailed") {
                setContent {
                    StartScreen {
                        PreConnectionScreen()
                    }
                }
            },
            EventAction("ApiError")
            {
                val message = ProgressEvents.getPayload("ApiError") as String?
                    ?: "ApiError! Something went wrong - Make sure the official G-Shock app in not running, to prevent interference."

                AppSnackbar(message)
                api().disconnect()
                setContent {
                    StartScreen {
                        PreConnectionScreen()
                    }
                }
            },
            EventAction("WaitForConnection")
            {
                setContent {
                    RunWithChecks()
                }
            },
            EventAction("Disconnect")
            {
                InactivityWatcher.cancel()
                val device = ProgressEvents.getPayload("Disconnect") as? BluetoothDevice
                if (device != null) {
                    api().teardownConnection(device)
                }

                Executors.newSingleThreadScheduledExecutor().schedule({
                    setContent {
                        RunWithChecks()
                    }
                }, 3L, TimeUnit.SECONDS)
            },
            EventAction("HomeTimeUpdated")
            {},
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private suspend fun waitForConnectionCached() {

        // Use this variable to control whether we should try to scan each time for the watch,
        // or reuse the last saved address. If set tto false, the connection is a bit slower,
        // but the app can connect to multiple watches without pressing "FORGET".
        // Also, auto-time-sync will work for multiple watches

        // Note: Consequently, we discovered that the Bluetooth scanning cannot be performed in the background,
        // so actions will fail. If this flag is true, no scanning will be performed.
        // Leave it to true.
        val reuseAddress = true
        var deviceAddress: String? = null

        if (reuseAddress) {
            val savedDeviceAddress =
                LocalDataStorage.get(applicationContext(), "LastDeviceAddress", "")
            if (api().validateBluetoothAddress(savedDeviceAddress)) {
                deviceAddress = savedDeviceAddress
            }
        }

        val deviceName = LocalDataStorage.get(applicationContext(), "LastDeviceName", "")
        api().waitForConnection(deviceAddress, deviceName)
    }

    private val requestBluetooth: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                AppSnackbar("Bluetooth enabled.")
            } else {
                CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                    AppSnackbar("Please enable Bluetooth in your settings and try again")
                    finish()
                }
            }
        }

    private fun checkIfBluetoothEnabled() {
        if (!api.isBluetoothEnabled(this)) {
            bluetoothHelper = BluetoothHelper(
                context = this,
                activity = this,
                requestBluetooth = requestBluetooth,
                onBluetoothEnabled = { AppSnackbar("Bluetooth enabled.") },
                onBluetoothNotEnabled = {
                    CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                        AppSnackbar("Please enable Bluetooth in your settings and try again")
                        finish()
                    }
                }
            )

            // Call turnOnBLE as needed
            bluetoothHelper.turnOnBLE()
        }
    }

    companion object {

        private var instance: MainActivity? = null

        // Make context available from anywhere in the code (not yet used).
        fun applicationContext(): Context {
            return instance!!.applicationContext
        }

        // git fun api(): GShockAPIMock {
        fun api(): GShockAPI {
            return instance!!.api
        }
    }
}
