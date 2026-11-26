package org.avmedia.gshockGoogleSync

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.PopupMessageReceiver
import org.avmedia.gshockGoogleSync.utils.BluetoothHelper
import org.avmedia.gshockGoogleSync.utils.CheckPermissions
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var watchFeatureManager: IWatchFeatureManager

    @Inject
    lateinit var pluginHub: org.avmedia.gshockGoogleSync.plugins.PluginHub

    /** The plugins' permission requests, one at a time: each answer goes back to the plugin that asked. */
    private val pluginAsks = org.avmedia.gshockGoogleSync.plugins.PermissionQueue<org.avmedia.gshockGoogleSync.plugins.PluginHub.PermissionRequest>()

    private val application: GShockApplication
        get() = applicationContext as GShockApplication
    private lateinit var bluetoothHelper: BluetoothHelper

    override fun onResume() {
        super.onResume()
        // A permission granted or revoked in the system settings shows when the app is back in front.
        pluginHub.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setupGlobalExceptionHandler()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val requestBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                bluetoothHelper.handleBluetoothResult(result.resultCode)
            }

        // Plugins' permissions: only the activity can ask (runtime permissions, or a plugin's own screen such as Health Connect's).
        lateinit var ask: (org.avmedia.gshockGoogleSync.plugins.PluginHub.PermissionRequest) -> Unit
        val answered = {
            val (done, next) = pluginAsks.answered()
            done?.let { pluginHub.permissionsAnswered(it.pluginId) }
            next?.let(ask)
        }
        val pluginPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answered() }
        val pluginPermissionScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { answered() }
        ask = { r ->
            runCatching { if (r.intent != null) pluginPermissionScreen.launch(r.intent) else pluginPermissions.launch(r.permissions.toTypedArray()) }
                .onFailure { answered() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                pluginHub.permissionRequests.collect { r -> pluginAsks.offer(r)?.let(ask) }
            }
        }

        bluetoothHelper =
            BluetoothHelper(
                context = this,
                activity = this,
                requestBluetooth = requestBluetoothLauncher,
                onBluetoothEnabled = {},
                onBluetoothNotEnabled = {
                    AppSnackbar(getString(R.string.bluetooth_is_not_enabled))
                    Executors.newSingleThreadScheduledExecutor()
                        .schedule({ finish() }, 3L, TimeUnit.SECONDS)
                }
            )

        setContent {
            CompositionLocalProvider(LocalWatchFeatureManager provides watchFeatureManager) {
                GShockSmartSyncTheme {
                    var isInitialized by remember { mutableStateOf(false) }

                    CheckPermissions {
                        LaunchedEffect(Unit) {
                            if (!isInitialized) {
                                bluetoothHelper.turnOnBLE {
                                    application.init()
                                    isInitialized = true
                                }
                            }
                        }

                        if (isInitialized) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets =
                                    androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                            ) { contentPadding ->
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    AppNavigationHost(contentPadding)
                                    PopupMessageReceiver()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "Uncaught exception on thread: ${thread.name}")
                Timber.e(throwable, "Uncaught exception on thread: ${thread.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to log uncaught exception")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Timber.i("Global exception handler installed")
    }
}
