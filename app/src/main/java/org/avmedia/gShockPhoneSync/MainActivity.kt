/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 4:50 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.avmedia.gShockPhoneSync.ble.BleScanner
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.casioB5600.WatchDataCollector
import org.avmedia.gShockPhoneSync.databinding.ActivityMainBinding
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataListener
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleScanner: BleScanner
    private lateinit var permissionManager: PermissionManager

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

        ScreenSelector.add("connect screen", binding.connectionLayout)
        ScreenSelector.add("g-shock screen", binding.mainLayout)

        ScreenSelector.showScreen("connect screen")

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_gshock_screens)
        navView.setupWithNavController(navController)

        createAppEventsSubscription()

        bleScanner = BleScanner(this)
        Connection.init(this)
        WatchDataListener.init()
    }

    override fun onResume() {
        super.onResume()

        if (permissionManager.hasAllPermissions()) {
            ProgressEvents.onNext(ProgressEvents.Events.AllPermissionsAccepted)
            bleScanner.startConnection()
        }
        if (!bleScanner.bluetoothAdapter.isEnabled) {
            permissionManager.promptEnableBluetooth()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        InactivityWatcher.resetTimer(this)
    }

    private fun onSuccess() {
        Timber.i("Permission granted...")
    }

    private fun onFail() {
        Timber.i("Permission failed...")
        Utils.toast(this, "Permission not granted...exiting")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == 0 }) {
            ProgressEvents.onNext(ProgressEvents.Events.AllPermissionsAccepted)
            bleScanner.startConnection()
        } else {
            Timber.i("Not all permissions granted...")
            Utils.toast(this, "Not all permissions granted, exiting...")
            val reconnectScheduler: ScheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor()
            reconnectScheduler.schedule({ finish() }, 1L, TimeUnit.SECONDS)
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
                    ProgressEvents.Events.PhoneDataCollected -> {
                        // We have collected all data from watch.
                        // Send initializer data to watch, se we can set time later
                        WatchDataCollector.runInitCommands()
                        ProgressEvents.onNext(ProgressEvents.Events.PhoneInitializationCompleted)
                        InactivityWatcher.start(this)
                    }
                    ProgressEvents.Events.Disconnect -> {
                        Timber.i("onDisconnect")
                        InactivityWatcher.cancel()

                        Utils.toast(this, "Disconnected from watch!")
                        val device = ProgressEvents.Events.Disconnect.payload as BluetoothDevice
                        Connection.teardownConnection(device)

                        // restart after 5 seconds
                        val reconnectScheduler: ScheduledExecutorService =
                            Executors.newSingleThreadScheduledExecutor()
                        reconnectScheduler.schedule({
                            bleScanner.startConnection()
                        }, 5L, TimeUnit.SECONDS)
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }
}
