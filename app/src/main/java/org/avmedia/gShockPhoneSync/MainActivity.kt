package org.avmedia.gShockPhoneSync

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.avmedia.gShockPhoneSync.services.DeviceManager
import org.avmedia.gShockPhoneSync.theme.GShockSmartSyncTheme
import org.avmedia.gShockPhoneSync.ui.common.SnackbarController
import org.avmedia.gShockPhoneSync.utils.CheckPermissions
import org.avmedia.gshockapi.GShockAPI

class MainActivity : ComponentActivity() {
    private val application: GShockApplication
        get() = applicationContext as GShockApplication
    private var deviceManager: DeviceManager
    private val api = GShockAPI(this)

    init {
        instance = this

        // do not delete this. DeviceManager needs to be running to save the last device name to reuse on next start.
        deviceManager = DeviceManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        application.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CheckPermissions {

                GShockSmartSyncTheme {
                    SnackbarController.snackbarHostState = remember { SnackbarHostState() }
                    Scaffold(
                        snackbarHost = {
                            SnackbarController.snackbarHostState?.let { nonNullHostState ->
                                SnackbarHost(hostState = nonNullHostState)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { padding ->
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            setContent {
                                application.Run()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        private var instance: MainActivity? = null

        // Make context available from anywhere in the code (not yet used).
        fun applicationContext(): Context {
            return instance!!.applicationContext
        }

        // git fun api(): GShockAPIMock {
        fun api(): GShockAPI {
            return instance!!.api
        }
    }
}


