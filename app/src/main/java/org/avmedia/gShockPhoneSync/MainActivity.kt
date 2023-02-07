/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 4:50 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import org.avmedia.gShockPhoneSync.databinding.ActivityMainBinding
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.utils.ProgressEvents
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private val api = GShockAPI(this)

    init {
        instance = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        permissionManager = PermissionManager(this)
        permissionManager.setupPermissions()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_gshock_screens)
        navView.setupWithNavController(navController)

        createAppEventsSubscription()

        // This will run in the foreground, but not reliable. Do not use for now.
        // val intent = Intent(this, ForegroundService::class.java)
        // this.startService(intent)

        // run()
        // ApiTest().run(this)
    }

    private fun run() {

        val scope = CoroutineScope(Dispatchers.Default)

        scope.launch {
            waitForConnectionCached()
            api().init(this@MainActivity)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onResume() {
        super.onResume()

        if (!api().isBluetoothEnabled()) {
            permissionManager.promptEnableBluetooth()
            return
        }

        if (permissionManager.hasAllPermissions()) {
            run()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        InactivityWatcher.resetTimer(this)
    }

    @SuppressLint("RestrictedApi")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(grantResults)

        if (grantResults.all { it == 0 }) {
            run()
        } else {
            Timber.i("Not all permissions granted...")
            Utils.snackBar(this, "Not all permissions granted, exiting...")

            Timer("SettingUp", false).schedule(2000) {
                finish()
            }
        }
    }

    private fun createAppEventsSubscription() {
        ProgressEvents.subscriber.start(this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents.Events.ConnectionSetupComplete -> {
                        InactivityWatcher.start(this)
                    }

                    ProgressEvents.Events.Disconnect -> {
                        Timber.i("onDisconnect")
                        InactivityWatcher.cancel()

                        Utils.snackBar(this, "Disconnected from watch!")
                        val device = ProgressEvents.Events.Disconnect.payload as BluetoothDevice
                        api().teardownConnection(device)

                        // restart after 5 seconds
                        val reconnectScheduler: ScheduledExecutorService =
                            Executors.newSingleThreadScheduledExecutor()
                        reconnectScheduler.schedule({
                            run()
                        }, 5L, TimeUnit.SECONDS)
                    }

                    ProgressEvents.Events.ConnectionFailed -> {
                        run()
                    }

                    ProgressEvents.Events.WatchInitializationCompleted -> {
                        val navController =
                            findNavController(R.id.nav_host_fragment_activity_gshock_screens)
                        navController.navigate(R.id.navigation_home)
                    }
                }
            }, { throwable ->
                Timber.d("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
    }

    private suspend fun waitForConnectionCached() {
        var cachedDeviceAddress: String? =
            LocalDataStorage.get("cached device", null, this@MainActivity)
        api().waitForConnection(cachedDeviceAddress)
        LocalDataStorage.put("cached device", api().getDeviceId(), this@MainActivity)
    }

    companion object {
        private var instance: MainActivity? = null

        // Make context available from anywhere in the code (not yet used).
        fun applicationContext(): Context {
            return instance!!.applicationContext
        }

        fun api(): GShockAPI {
            return instance!!.api
        }
    }
}

