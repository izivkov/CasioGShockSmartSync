package org.avmedia.gshockGoogleSync

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.MainActivity.Companion.api
import org.avmedia.gshockGoogleSync.MainActivity.Companion.applicationContext
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.PopupMessageReceiver
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionScreen
import org.avmedia.gshockGoogleSync.ui.others.RunActionsScreen
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class GShockApplication : Application() {
    lateinit var context: MainActivity
    val api: GShockAPI by lazy { GShockAPI(this) }

    fun init(context: MainActivity) {
        this.context = context
    }

    override fun onCreate() {
        super.onCreate()

        DeviceManager.initialize(api)
        createAppEventsSubscription()
    }

    @Composable
    fun Run() {
        StartScreen { PreConnectionScreen() }

        // Ensure a new LaunchedEffect is triggered each time Run() is called by using a unique key
        LaunchedEffect(key1 = System.currentTimeMillis()) {
            waitForConnection()
        }
    }

    private fun createAppEventsSubscription() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
            },
            EventAction("WatchInitializationCompleted") { handleWatchInitialization() },
            EventAction("ConnectionFailed") { handleConnectionFailure() },
            EventAction("ApiError") { handleApiError() },
            EventAction("WaitForConnection") { waitForConnectionSuspended() },
            EventAction("Disconnect") { handleDisconnect() },
            EventAction("HomeTimeUpdated") {}
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private fun handleWatchInitialization() {
        context.setContent {
            StartScreen {
                if (api.isActionButtonPressed() || api.isAutoTimeStarted() || api.isFindPhoneButtonPressed()) {
                    RunActionsScreen()
                } else {
                    BottomNavigationBarWithPermissions()
                }
            }
        }
    }

    @Composable
    fun StartScreen(content: @Composable () -> Unit) {
        GShockSmartSyncTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
                PopupMessageReceiver()
            }
        }
    }

    private fun handleConnectionFailure() {
        context.setContent {
            StartScreen {
                PreConnectionScreen()
            }
        }
    }

    private fun handleApiError() {
        val message = ProgressEvents.getPayload("ApiError") as String?
            ?: "ApiError! Ensure the official G-Shock app is not running."

        AppSnackbar(message)
        api.disconnect()
        context.setContent {
            StartScreen {
                PreConnectionScreen()
            }
        }
    }

    private fun handleDisconnect() {
        val device = ProgressEvents.getPayload("Disconnect") as? BluetoothDevice
        if (device != null) {
            api.teardownConnection(device)
        }

        Executors.newSingleThreadScheduledExecutor().schedule({
            context.setContent {
                Run()
            }
        }, 3L, TimeUnit.SECONDS)
    }

    private fun waitForConnectionSuspended() {
        CoroutineScope(Dispatchers.Default).launch {
            waitForConnection()
        }
    }

    private suspend fun waitForConnection() {

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
}
