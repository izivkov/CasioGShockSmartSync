package org.avmedia.gshockGoogleSync

import android.annotation.SuppressLint
import android.app.Application
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDevicePresenceMonitor
import org.avmedia.gshockGoogleSync.pairing.DeviceAssociationManager
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.ui.actions.ActionRunner
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.CrashLogDialog
import org.avmedia.gshockGoogleSync.ui.others.CoverScreen
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionScreen
import org.avmedia.gshockGoogleSync.ui.others.RunActionsScreen
import org.avmedia.gshockGoogleSync.ui.others.RunFindPhoneScreen
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import timber.log.Timber
import javax.inject.Inject

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

    private lateinit var eventHandler: MainEventHandler

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var repository: GShockRepository

    @Inject
    lateinit var companionDevicePresenceMonitor: CompanionDevicePresenceMonitor

    @Inject
    lateinit var deviceAssociationManager: DeviceAssociationManager

    fun init() {
        Timber.i("Initializing GShockApplication")
        CoroutineScope(Dispatchers.IO).launch {
            deviceAssociationManager.init()
        }
    }


    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        ActivityProvider.initialize(this)
        eventHandler =
            MainEventHandler(context = this, repository = repository, screenManager = this)
        eventHandler.setupEventSubscription()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber planted for Debug") // If you see this, Timber is working
        } else {
            // Optional: Plant a tree that only logs Errors to a crash reporter like Firebase
            // Timber.plant(ReleaseTree())
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
        LaunchedEffect(key1 = System.currentTimeMillis()) {
            deviceAssociationManager.checkPairedDevicesOrNotify()
        }
    }

    @Composable
    fun StartScreen(contentPadding: PaddingValues, content: @Composable () -> Unit) {
        deviceAssociationManager.pendingCrashLog?.let { log ->
            CrashLogDialog(
                crashLog = log,
                onDismiss = { deviceAssociationManager.pendingCrashLog = null })
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)) { content() }
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
