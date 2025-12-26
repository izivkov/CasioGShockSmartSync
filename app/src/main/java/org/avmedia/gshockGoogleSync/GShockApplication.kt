package org.avmedia.gshockGoogleSync

import android.annotation.SuppressLint
import android.app.Application
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDevicePresenceMonitor
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.services.GShockScanService
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
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GShockApplication : Application(), IScreenManager {
    private var _context: MainActivity? = null
    private val context: MainActivity?
        get() = _context ?: ActivityProvider.getCurrentActivity() as? MainActivity

    private fun safeSetContent(content: @Composable () -> Unit) {
        context?.setContent { content() } ?: Timber.w("Cannot set content: MainActivity is not available")
    }
    private lateinit var eventHandler: MainEventHandler

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var repository: GShockRepository

    @Inject
    lateinit var companionDevicePresenceMonitor: CompanionDevicePresenceMonitor

    fun init(context: MainActivity) {

        _context = context

        CoroutineScope(Dispatchers.IO).launch {

            cleanupLocalStorage(context)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val intent = Intent(context, GShockScanService::class.java)
                context.startService(intent)
            } else {
                syncAssociations()
            }
        }
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        ActivityProvider.initialize(this)
        eventHandler = MainEventHandler(
            context = this,
            repository = repository,
            screenManager = this
        )
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
                LocalDataStorage.addDeviceAddress(
                    this@GShockApplication,
                    association.address
                )

                association.name?.let {
                    LocalDataStorage.setDeviceName(
                        this@GShockApplication,
                        association.address,
                        it
                    )
                }
            }

            if (LocalDataStorage.get(
                    this@GShockApplication,
                    "LastDeviceAddress",
                    ""
                ).isNullOrEmpty()
            ) {
                associations.firstOrNull()?.let {
                    LocalDataStorage.put(
                        this@GShockApplication,
                        "LastDeviceAddress",
                        it.address
                    )
                }
            }

            // ✅ SAFE, explicit API gating
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                associations.forEach { association ->
                    repository.startObservingDevicePresence(this@GShockApplication, association.address)
                }
            } else {
                Timber.i(
                    "Skipping device presence observation on API ${Build.VERSION.SDK_INT}"
                )
            }
        }
    }

    // ScreenManager implementation
    override fun showContentSelector(repository: GShockRepository) {
        safeSetContent {
            StartScreen {
                ContentSelector(
                    repository = repository,
                    onUnlocked = { goToNavigationScreen() }
                )
            }
        }
    }

    override fun showRunActionsScreen() {
        safeSetContent {
            StartScreen {
                RunActionsScreen()
            }
        }
    }

    private fun goToNavigationScreen() {
        safeSetContent {
            StartScreen {
                BottomNavigationBarWithPermissions(
                    repository = repository
                )
            }
        }
    }

    override fun showPreConnectionScreen() {
        safeSetContent {
            StartScreen {
                PreConnectionScreen()
            }
        }
    }

    override fun showInitialScreen() {
        safeSetContent {
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

    @SuppressLint("NewApi")
    internal suspend fun waitForConnection() {
        val associations = repository.getAssociationsWithNames(this)
        if (associations.isEmpty() && LocalDataStorage.getDeviceAddresses(this).isEmpty()) {
            ProgressEvents.onNext("NoPairedDevices")
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

