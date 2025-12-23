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
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
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
    lateinit var repository: GShockRepository

    @Inject
    lateinit var companionDevicePresenceMonitor: CompanionDevicePresenceMonitor

    fun init(context: MainActivity) {

        _context = context
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val intent = Intent(context, GShockScanService::class.java)
            context.startService(intent)
        } else {
            syncAssociations()
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
                ).isNullOrEmpty() && associations.isNotEmpty()
            ) {
                LocalDataStorage.put(
                    this@GShockApplication,
                    "LastDeviceAddress",
                    associations[0].address
                )
            }

            // ✅ SAFE, explicit API gating
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startObservingDevicePresence(this@GShockApplication, associations[0].address)
            } else {
                Timber.i(
                    "Skipping device presence observation on API ${Build.VERSION.SDK_INT}"
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    internal fun startObservingDevicePresence(
        context: Context,
        address: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Timber.i(
                "Device presence observation not supported on API ${Build.VERSION.SDK_INT}"
            )
            return
        }

        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE)
                    as? CompanionDeviceManager
                ?: return

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val association = deviceManager.myAssociations.firstOrNull {
                    it.deviceMacAddress?.toString()
                        ?.equals(address, ignoreCase = true) == true
                } ?: run {
                    Timber.w("No association found for address: $address")
                    return
                }

                val request = ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(association.id)
                    .build()

                deviceManager.startObservingDevicePresence(request)

                Timber.i(
                    "Started observing device presence (API 33+) for associationId=${association.id}"
                )
            }

            Build.VERSION.SDK_INT == Build.VERSION_CODES.S -> {
                // Android 12 / 12L
                startObservingAndroid12(deviceManager, address)
            }

            else -> {
                Timber.i(
                    "Device presence observation not supported on API ${Build.VERSION.SDK_INT}"
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun startObservingAndroid12(
        deviceManager: CompanionDeviceManager,
        address: String
    ) {
        deviceManager.startObservingDevicePresence(address)

        Timber.i("Started observing device presence (API 31–32) for: $address")
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

