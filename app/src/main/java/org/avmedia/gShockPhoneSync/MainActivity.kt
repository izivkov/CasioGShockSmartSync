/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 4:50 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.avmedia.gShockPhoneSync.ble.BleScannerLocal
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.casioB5600.WatchDataCollector
import org.avmedia.gShockPhoneSync.customComponents.ActionsModel
import org.avmedia.gShockPhoneSync.databinding.ActivityMainBinding
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataListener
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleScannerLocal: BleScannerLocal
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bleScannerLocal = BleScannerLocal(this)

        permissionManager = PermissionManager(this)
        permissionManager.setupPermissions()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_gshock_screens)
        navView.setupWithNavController(navController)

        createAppEventsSubscription()

        Connection.init(this)
        WatchDataListener.init()

        // This will run in the foreground, but not reliable. Do not use for now.
        // val intent = Intent(this, ForegroundService::class.java)
        // this.startService(intent)

        if (Utils.isDebugMode()) {
            navController.navigate(org.avmedia.gShockPhoneSync.R.id.navigation_actions)
        }
    }

    override fun onResume() {
        super.onResume()

        if (!Utils.isDebugMode()) {

            if (!bleScannerLocal.bluetoothAdapter.isEnabled) {
                permissionManager.promptEnableBluetooth()
                return
            }

            if (!isLocationEnabled(this)) {
                showLocationIsDisabledAlert()
                return
            }

            if (permissionManager.hasAllPermissions()) {
                bleScannerLocal.startConnection()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        InactivityWatcher.resetTimer(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(grantResults)

        if (grantResults.all { it == 0 }) {
            bleScannerLocal.startConnection()
        } else {
            Timber.i("Not all permissions granted...")
            Utils.snackBar(this, "Not all permissions granted, exiting...")

            Timer("SettingUp", false).schedule(2000) {
                finish()
            }
        }
    }

    private fun createAppEventsSubscription() {
        ProgressEvents.subscriber.start(
            this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents.Events.ConnectionSetupComplete -> {
                        val device =
                            ProgressEvents.Events.ConnectionSetupComplete.payload as BluetoothDevice
                        DeviceCharacteristics.init(device)
                        CasioSupport.init()
                    }
                    ProgressEvents.Events.WatchDataCollected -> {

                        // We have collected all data from watch.
                        // Send initializer data to watch, se we can set time later
                        WatchDataCollector.runInitCommands()
                        ProgressEvents.onNext(ProgressEvents.Events.WatchInitializationCompleted)

                        InactivityWatcher.start(this)
                    }

                    ProgressEvents.Events.ButtonPressedInfoReceived -> {
                        if (CasioSupport.isActionButtonPressed()) {
                            ActionsModel.loadData(this)
                            ActionsModel.runActions(this)
                        }
                    }
                    ProgressEvents.Events.Disconnect -> {
                        Timber.i("onDisconnect")
                        InactivityWatcher.cancel()

                        Utils.snackBar(this, "Disconnected from watch!")
                        val device = ProgressEvents.Events.Disconnect.payload as BluetoothDevice
                        Connection.teardownConnection(device)

                        // restart after 5 seconds
                        val reconnectScheduler: ScheduledExecutorService =
                            Executors.newSingleThreadScheduledExecutor()
                        reconnectScheduler.schedule({
                            bleScannerLocal.startConnection()
                        }, 5L, TimeUnit.SECONDS)
                    }
                    ProgressEvents.Events.ConnectionFailed -> {
                        bleScannerLocal.startConnection()
                    }
                }
            },
            { throwable ->
                Timber.d("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
    }

    // location
    private fun isLocationEnabled(mContext: Context): Boolean {
        val lm = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showLocationIsDisabledAlert() {
        alert("This app requires your location setting to be enabled. Select \"SETTINGS\" to enable it.") {
            neutralPressed("Settings") {
                startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS))
            }
            noButton {
                Utils.snackBar(this@MainActivity, "Not all permissions granted, exiting...")
                finish()
            }
        }.show()
    }
}

