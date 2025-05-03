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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.services.KeepAliveManager
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.actions.ActionRunner
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
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GShockApplication : Application() {
    private var _context: MainActivity? = null
    private val context
        get() = _context ?: throw IllegalStateException("MainActivity not initialized")

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var translateApi: TranslateRepository

    @Inject
    lateinit var repository: GShockRepository

    override fun onCreate() {
        super.onCreate()
        setupEventSubscription()
    }

    fun init(context: MainActivity) {
        _context = context
        if (LocalDataStorage.getKeepAlive(context)) {
            KeepAliveManager.getInstance(context).enable()
        }
    }

    @Composable
    fun Run() {
        // Start ActionRunner here so we can run actions on connection
        ActionRunner(context = this, api = repository)

        StartScreen { PreConnectionScreen() }
        LaunchedEffect(key1 = System.currentTimeMillis()) {
            waitForConnection()
        }
    }

    @Composable
    fun StartScreen(content: @Composable () -> Unit) {
        GShockSmartSyncTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
                PopupMessageReceiver()
            }
        }
    }

    private fun setupEventSubscription() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {},
            EventAction("WatchInitializationCompleted") { handleWatchInitialization() },
            EventAction("ConnectionFailed") { handleConnectionFailure() },
            EventAction("ApiError") { handleApiError() },
            EventAction("WaitForConnection") { waitForConnectionSuspended() },
            EventAction("Disconnect") { handleDisconnect() },
            EventAction("HomeTimeUpdated") {},
            EventAction("RunActions") { handleRunAction() }
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private fun handleConnectionFailure() {
        Timber.e("Failed to connect to the watch")
    }

    private fun handleWatchInitialization() {
        context.setContent {
            StartScreen {
                ContentSelector(
                    repository = repository,
                    translateApi = translateApi,
                    onUnlocked = { goToNavigationScreen() }
                )
            }
        }
    }

    private fun handleRunAction() {
        context.setContent {
            StartScreen {
                RunActionsScreen(repository, translateApi)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            context.setContent {
                StartScreen {
                    ContentSelector(
                        repository = repository,
                        translateApi = translateApi,
                        onUnlocked = { goToNavigationScreen() }
                    )
                }
            }
        }
    }

    private fun goToNavigationScreen() {
        context.setContent {
            StartScreen {
                BottomNavigationBarWithPermissions(
                    repository = repository,
                    translateApi = translateApi
                )
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
        ProgressEvents.getPayload("Disconnect")?.let { device ->
            repository.teardownConnection(device as BluetoothDevice)
        }

        Executors.newSingleThreadScheduledExecutor().schedule({
            context.setContent { Run() }
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

        if (reuseAddress) {
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

    @Composable
    private fun ContentSelector(
        repository: GShockRepository,
        translateApi: TranslateRepository,
        onUnlocked: () -> Unit
    ) {
        when {
            repository.isAlwaysConnectedConnectionPressed() -> {
                CoverScreen(
                    translateApi = translateApi,
                    onUnlock = onUnlocked,
                    isConnected = repository.isConnected()
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

    override fun onTerminate() {
        super.onTerminate()
        _context = null
    }
}

