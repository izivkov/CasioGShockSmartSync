package org.avmedia.gshockGoogleSync

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dagger.hilt.android.HiltAndroidApp
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
import javax.inject.Inject

@HiltAndroidApp
class GShockApplication : Application(), IScreenManager {
    private var _context: MainActivity? = null
    private val context
    get() = _context ?: throw IllegalStateException("MainActivity not initialized")
    private lateinit var eventHandler: MainEventHandler

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var translateApi: TranslateRepository

    @Inject
    lateinit var repository: GShockRepository

    fun init(context: MainActivity) {
        _context = context
        if (LocalDataStorage.getKeepAlive(context)) {
            KeepAliveManager.getInstance(context).enable()
        }
    }

    override fun onCreate() {
        super.onCreate()
        eventHandler = MainEventHandler(
            context = this,
            repository = repository,
            translateApi = translateApi,
            screenManager = this
        )
        eventHandler.setupEventSubscription()
    }

    // ScreenManager implementation
    override fun showContentSelector(repository: GShockRepository, translateApi: TranslateRepository) {
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

    override fun showRunActionsScreen(translateApi: TranslateRepository) {
        context.setContent {
            StartScreen {
                RunActionsScreen(translateApi)
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

    override fun showPreConnectionScreen() {
        context.setContent {
            StartScreen {
                PreConnectionScreen()
            }
        }
    }

    override fun showInitialScreen() {
        context.setContent {
            Run()
        }
    }

    override fun showError(message: String) {
        AppSnackbar(message)
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
                RunActionsScreen(translateApi)
            }

            repository.isFindPhoneButtonPressed() -> {
                RunFindPhoneScreen(translateApi)
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

