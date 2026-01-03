package org.avmedia.gshockGoogleSync

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDevicePresenceMonitor
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.services.GShockScanService
import org.avmedia.gshockGoogleSync.ui.actions.ActionRunner
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.CrashLogDialog
import org.avmedia.gshockGoogleSync.ui.others.CoverScreen
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionScreen
import org.avmedia.gshockGoogleSync.ui.others.RunActionsScreen
import org.avmedia.gshockGoogleSync.ui.others.RunFindPhoneScreen
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import org.avmedia.gshockGoogleSync.utils.CrashReportHelper
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

enum class AppScreen {
    INITIAL,
    PRE_CONNECTION,
    CONTENT_SELECTOR,
    MAIN_NAVIGATION,
    RUN_ACTIONS,
    ERROR
}

@HiltAndroidApp
class GShockApplication : Application(), IScreenManager {

    var currentScreen by mutableStateOf(AppScreen.INITIAL)
    var errorMessage by mutableStateOf<String?>(null)
    var pendingCrashLog by mutableStateOf<String?>(null)

    private lateinit var eventHandler: MainEventHandler

    @Inject lateinit var deviceManager: DeviceManager

    @Inject lateinit var repository: GShockRepository

    @Inject lateinit var companionDevicePresenceMonitor: CompanionDevicePresenceMonitor

    fun init() {

        Timber.i("Initializing GShockApplication")

        CoroutineScope(Dispatchers.IO).launch {

            // Check for previous pairing crash and recover if needed
            recoverFromPairingCrash()

            cleanupLocalStorage(this@GShockApplication)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val intent = Intent(this@GShockApplication, GShockScanService::class.java)
                startService(intent)
            } else {
                syncAssociations()
            }
        }
    }

    /** Recover from a previous pairing crash by clearing potentially corrupted state */
    private suspend fun recoverFromPairingCrash() {
        try {
            if (CrashReportHelper.hasPairingCrashFlag(this)) {
                Timber.w("Detected previous pairing crash, attempting recovery")

                val latestCrash = CrashReportHelper.getLatestCrashLog(this)

                // Set the pending log to be picked up by the UI
                if (latestCrash != null) {
                    pendingCrashLog = latestCrash
                    Timber.e("Previous crash log captured for display")
                }

                CrashReportHelper.clearPairingCrashFlag(this)
                Timber.i("Pairing crash recovery completed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to recover from pairing crash")
        }
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        ActivityProvider.initialize(this)
        eventHandler =
                MainEventHandler(context = this, repository = repository, screenManager = this)
        eventHandler.setupEventSubscription()
    }

    suspend fun cleanupLocalStorage(context: Context) {
        val repositoryAssociations = repository.getAssociations(context)
        val localAddresses = LocalDataStorage.getDeviceAddresses(context)

        // 1. Identify which addresses SHOULD be removed
        val addressesToRemove = localAddresses.filter { it !in repositoryAssociations }

        // 2. Remove them one by one (Safe here because we aren't modifying 'localAddresses')
        addressesToRemove.forEach { address ->
            Timber.i("Cleaning up orphaned local association: $address")
            LocalDataStorage.removeDeviceAddress(context, address)
        }
    }

    private fun syncAssociations() {
        val associations = repository.getAssociationsWithNames(this)

        CoroutineScope(Dispatchers.IO).launch {
            associations.forEach { association ->
                LocalDataStorage.addDeviceAddress(this@GShockApplication, association.address)

                association.name?.let {
                    LocalDataStorage.setDeviceName(this@GShockApplication, association.address, it)
                }
            }

            if (LocalDataStorage.get(this@GShockApplication, "LastDeviceAddress", "")
                            .isNullOrEmpty()
            ) {
                associations.firstOrNull()?.let {
                    LocalDataStorage.put(this@GShockApplication, "LastDeviceAddress", it.address)
                }
            }

            // ✅ SAFE, explicit API gating
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                associations.forEach { association ->
                    repository.startObservingDevicePresence(
                            this@GShockApplication,
                            association.address
                    )
                }
            } else {
                Timber.i("Skipping device presence observation on API ${Build.VERSION.SDK_INT}")
            }
        }
    }

    // ScreenManager implementation
    override fun showContentSelector(repository: GShockRepository) {
        currentScreen = AppScreen.CONTENT_SELECTOR
    }

    override fun showRunActionsScreen() {
        currentScreen = AppScreen.RUN_ACTIONS
    }

    private fun goToNavigationScreen() {
        currentScreen = AppScreen.MAIN_NAVIGATION
    }

    override fun showPreConnectionScreen() {
        currentScreen = AppScreen.PRE_CONNECTION
    }

    override fun showInitialScreen() {
        currentScreen = AppScreen.INITIAL
    }

    override fun showError(message: String) {
        errorMessage = message
        currentScreen = AppScreen.ERROR
        AppSnackbar(message)
    }

    @Composable
    fun Run(contentPadding: PaddingValues) {
        // Start ActionRunner here so we can run actions on connection
        ActionRunner(context = this, api = repository)

        StartScreen(contentPadding) { PreConnectionScreen() }
        LaunchedEffect(key1 = System.currentTimeMillis()) { waitForConnection() }
    }

    @Composable
    fun StartScreen(contentPadding: PaddingValues, content: @Composable () -> Unit) {
        pendingCrashLog?.let { log ->
            CrashLogDialog(crashLog = log, onDismiss = { pendingCrashLog = null })
        }

        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) { content() }
    }

    @SuppressLint("NewApi")
    internal suspend fun waitForConnection() {
        val associations = repository.getAssociationsWithNames(this)
        if (associations.isEmpty() && LocalDataStorage.getDeviceAddresses(this).isEmpty()) {
            ProgressEvents.onNext("NoPairedDevices")
        }
    }

    @Composable
    fun AppContainer(contentPadding: PaddingValues) {
        when (currentScreen) {
            AppScreen.INITIAL -> Run(contentPadding)
            AppScreen.PRE_CONNECTION -> StartScreen(contentPadding) { PreConnectionScreen() }
            AppScreen.CONTENT_SELECTOR ->
                    StartScreen(contentPadding) {
                        ContentSelector(
                                repository = repository,
                                onUnlocked = { goToNavigationScreen() }
                        )
                    }
            AppScreen.MAIN_NAVIGATION ->
                    StartScreen(contentPadding) {
                        BottomNavigationBarWithPermissions(repository = repository)
                    }
            AppScreen.RUN_ACTIONS -> StartScreen(contentPadding) { RunActionsScreen() }
            AppScreen.ERROR -> {
                // Keep showing previous screen or a default one, but show error snackbar
                // Or show a specific error screen if needed
                StartScreen(contentPadding) { PreConnectionScreen() }
            }
        }
    }

    @Composable
    private fun ContentSelector(repository: GShockRepository, onUnlocked: () -> Unit) {
        when {
            repository.isAlwaysConnectedConnectionPressed() -> {
                CoverScreen(onUnlock = onUnlocked, isConnected = repository.isConnected())
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
}
