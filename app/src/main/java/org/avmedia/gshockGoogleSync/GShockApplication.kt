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
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.di.RepositoryEntryPoint
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.services.KeepAliveManager
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.PopupMessageReceiver
import org.avmedia.gshockGoogleSync.ui.others.CoverScreen
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionScreen
import org.avmedia.gshockGoogleSync.ui.others.RunActionsScreen
import org.avmedia.gshockGoogleSync.ui.others.RunFindPhoneScreen
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GShockApplication : Application() {
    lateinit var context: MainActivity
    private lateinit var repository: GShockRepository

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var translateApi: TranslateRepository

    fun init(context: MainActivity) {
        this.context = context

        if (LocalDataStorage.getKeepAlive(context)) {
            KeepAliveManager.getInstance(context).enable()
        }
    }

    override fun onCreate() {
        super.onCreate()

        repository = EntryPointAccessors.fromApplication(
            this,
            RepositoryEntryPoint::class.java
        ).getGShockRepository()

        deviceManager
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
            EventAction("ConnectionSetupComplete") {},
            EventAction("WatchInitializationCompleted") { handleWatchInitialization() },
            EventAction("ConnectionFailed") { handleConnectionFailure() },
            EventAction("ApiError") { handleApiError() },
            EventAction("WaitForConnection") { waitForConnectionSuspended() },
            EventAction("Disconnect") { handleDisconnect() },
            EventAction("HomeTimeUpdated") {}
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    @Composable
    private fun ContentSelector(
        repository: GShockRepository,
        translateApi: TranslateRepository,
        onUnlocked: () -> Unit
    ) {
        when {
            repository.isAlwaysConnectedConnectionPressed() -> {
                CoverScreen(
                    repository = repository,
                    translateApi = translateApi,
                    onUnlocked = onUnlocked
                )
            }
            repository.isActionButtonPressed() || repository.isAutoTimeStarted() -> {
                RunActionsScreen(repository, translateApi)
            }
            repository.isFindPhoneButtonPressed() -> {
                RunFindPhoneScreen(repository, translateApi)
            }
            else -> {
                BottomNavigationBarWithPermissions(
                    repository = repository,
                    translateApi = translateApi
                )
            }
        }
    }

    private fun handleWatchInitialization() {
        context.setContent {
            StartScreen {
                ContentSelector(
                    repository = repository,
                    translateApi = translateApi,
                    onUnlocked = {
                        context.setContent {
                            StartScreen {
                                BottomNavigationBarWithPermissions(
                                    repository = repository,
                                    translateApi = translateApi
                                )
                            }
                        }
                    }
                )
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
            ?: translateApi.getString(
                context,
                R.string.apierror_ensure_the_official_g_shock_app_is_not_running
            )

        AppSnackbar(message)
        repository.disconnect()
        context.setContent {
            StartScreen {
                PreConnectionScreen()
            }
        }
    }

    private fun handleDisconnect() {
        val device = ProgressEvents.getPayload("Disconnect") as? BluetoothDevice
        if (device != null) {
            repository.teardownConnection(device)
        }

        Executors.newSingleThreadScheduledExecutor().schedule({
            context.setContent {
                Run()
            }
        }, 0L, TimeUnit.SECONDS)
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

        if (reuseAddress){
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter?.isEnabled == true) {
                val savedDeviceAddress = LocalDataStorage.get(this, "LastDeviceAddress", "")
                if (repository.validateBluetoothAddress(savedDeviceAddress)) {
                    deviceAddress = savedDeviceAddress
                }
            }
        }
        repository.waitForConnection(deviceAddress)
    }
}
