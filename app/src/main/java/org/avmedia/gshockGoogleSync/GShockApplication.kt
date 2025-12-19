package org.avmedia.gshockGoogleSync

import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.ble.GShockPairingManager

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
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import javax.inject.Inject
import org.avmedia.gshockapi.ProgressEvents

@HiltAndroidApp
class GShockApplication : Application(), IScreenManager {
    private var _context: MainActivity? = null
    private val context
        get() = _context ?: throw IllegalStateException("MainActivity not initialized")
    private lateinit var eventHandler: MainEventHandler

    @Inject
    lateinit var deviceManager: DeviceManager

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
        ActivityProvider.initialize(this)
        eventHandler = MainEventHandler(
            context = this,
            repository = repository,
            screenManager = this
        )
        eventHandler.setupEventSubscription()
        syncAssociations()
    }

    private fun syncAssociations() {
        val associations = GShockPairingManager.getAssociations(this)
        CoroutineScope(Dispatchers.IO).launch {
            associations.forEach { address ->
                LocalDataStorage.addDeviceAddress(this@GShockApplication, address)
            }

            // Also ensure the last connected device is set if nothing is set
            val lastAddress = LocalDataStorage.get(this@GShockApplication, "LastDeviceAddress", "")
            if (lastAddress.isNullOrEmpty() && associations.isNotEmpty()) {
                LocalDataStorage.put(this@GShockApplication, "LastDeviceAddress", associations[0])
            }

            startObservingDevicePresence()
        }
    }

    private fun startObservingDevicePresence() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
            if (deviceManager != null) {
                val addresses = LocalDataStorage.getDeviceAddresses(this)
                for (address in addresses) {
                    try {
                        deviceManager.startObservingDevicePresence(address)
                        Timber.i("Started observing device presence for: $address")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to start observing device presence for: $address")
                    }
                }
            }
        }
    }

    // ScreenManager implementation
    override fun showContentSelector(repository: GShockRepository) {
        context.setContent {
            StartScreen {
                ContentSelector(
                    repository = repository,
                    onUnlocked = { goToNavigationScreen() }
                )
            }
        }
    }

    override fun showRunActionsScreen() {
        context.setContent {
            StartScreen {
                RunActionsScreen()
            }
        }
    }

    private fun goToNavigationScreen() {
        context.setContent {
            StartScreen {
                BottomNavigationBarWithPermissions(
                    repository = repository
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

    internal suspend fun waitForConnection() {
        // Use this variable to control whether we should try to scan each time for the watch,
        // or reuse the last saved address. If set to false, the connection is a bit slower,
        // but the app can connect to multiple watches without pressing "FORGET".
        // Also, auto-time-sync will work for multiple watches

        // Note: Consequently, we discovered that the Bluetooth scanning cannot be performed in the background,
        // so actions will fail. If this flag is true, no scanning will be performed.
        // Leave it to true.
        val reuseAddress = true

        if (reuseAddress) {
            val savedDeviceAddresses = LocalDataStorage.getDeviceAddresses(this)
            val lastDeviceAddress = LocalDataStorage.get(this, "LastDeviceAddress", "")

            // try last device first
            if (repository.validateBluetoothAddress(lastDeviceAddress)) {
                repository.waitForConnection(lastDeviceAddress)
            } else {
                // try other devices
                for (address in savedDeviceAddresses) {
                    if (repository.validateBluetoothAddress(address)) {
                        repository.waitForConnection(address)
                        if (repository.isConnected()) break
                    }
                }
            }

            // if still not connected and we have no associated devices, trigger pairing
            val associations = GShockPairingManager.getAssociations(this)
            if (!repository.isConnected() && savedDeviceAddresses.isEmpty() && associations.isEmpty()) {
                ProgressEvents.onNext("NoPairedDevices")
            } else if (!repository.isConnected()) {
                repository.waitForConnection("") // Fallback / Listen for any
            }
        } else {
            repository.waitForConnection("")
        }
    }

    @Composable
    private fun ContentSelector(
        repository: GShockRepository,
        onUnlocked: () -> Unit
    ) {
        when {
            repository.isAlwaysConnectedConnectionPressed() -> {
                CoverScreen(
                    onUnlock = onUnlocked,
                    isConnected = repository.isConnected()
                )
            }

            repository.isActionButtonPressed() || repository.isAutoTimeStarted() -> {
                RunActionsScreen()
            }

            repository.isFindPhoneButtonPressed() -> {
                RunFindPhoneScreen()
            }

            else -> {
                BottomNavigationBarWithPermissions(
                    repository = repository,
                )
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        _context = null
    }
}

