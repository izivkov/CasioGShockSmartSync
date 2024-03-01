/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 4:50 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.databinding.ActivityMainBinding
import org.avmedia.gShockPhoneSync.ui.time.HomeTime
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import java.util.Timer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private val api = GShockAPI(this)

    // do not delete this. DeviceManager needs to be running to save the last device name to reuse on next start.
    private var deviceManager: DeviceManager

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Utils.snackBar(this, "Bluetooth enabled.")
            } else {
                Utils.snackBar(this, "Please enable Bluetooth in your settings and ty again")
                finish()
            }
        }

    init {
        instance = this

        // do not delete this. DeviceManager needs to be running to save the last device name to reuse on nect start.
        deviceManager = DeviceManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.info.getInfoText()
            ?.let { binding.info.setInfoText(it + "v" + BuildConfig.VERSION_NAME) }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        permissionManager = PermissionManager(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setupNavigation()
        createAppEventsSubscription()

        // This will run in the foreground, but not reliable. Do not use for now.
        // val intent = Intent(this, ForegroundService::class.java)
        // this.startService(intent)

        // ApiTest().run(this)

        // TODO: remove this later
        LocalDataStorage.migrateSharedPreferencesToDataStore(this)
    }

    private fun run() {

        lifecycleScope.launch(Dispatchers.IO) {
            Timber.i("=============== >>>> *** Waiting for connection... ***")
            waitForConnectionCached()
        }
    }

    private fun runWithChecks() {

        if (!api().isBluetoothEnabled(this)) {
            turnOnBLE()
            return
        }

        if (!permissionManager.hasAllPermissions()) {
            permissionManager.setupPermissions()
            return
        }

        if (api().isConnected()) {
            return
        }

        run()
    }

    @SuppressLint("RestrictedApi")
    override fun onResume() {
        super.onResume()

        // This method is called when the main view is created,
        // and also when we complete a dialog for granting permissions.
        // We want to run the app only from the main screen, so
        // we do some checks in the runWithChecks() method.
        runWithChecks()
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_gshock_screens)
        navView.setupWithNavController(navController)
    }

    // @SuppressLint("MissingPermission")
    private fun turnOnBLE() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Utils.snackBar(this, "Sorry, your device does not support Bluetooth. Exiting...")
            Timer("SettingUp", false).schedule(6000) { finish() }
        }

        //val REQUEST_ENABLE_BT = 99
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            requestBluetooth.launch(enableBtIntent)
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
        permissionManager.onRequestPermissionsResult(permissions, grantResults)
    }

    private fun createAppEventsSubscription() {

        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                InactivityWatcher.start(this)
            },
            EventAction("ConnectionFailed") { runWithChecks() },
            EventAction("FineLocationPermissionNotGranted") {
                Utils.snackBar(
                    this,
                    "\"Fine Location\" Permission Not Granted! Clear the App's Cache to try again."
                )
                Timer("SettingUp", false).schedule(6000) {
                    finish()
                }
            },
            EventAction("FineLocationPermissionGranted") { Timber.i("FineLocationPermissionGranted") },
            EventAction("ApiError") {
                Utils.snackBar(
                    this,
                    "ApiError! Something went wrong - Make sure the official G-Shock app in not running, to prevent interference."
                )
                val errorScheduler: ScheduledExecutorService =
                    Executors.newSingleThreadScheduledExecutor()
                errorScheduler.schedule({
                    api().disconnect(this)
                }, 3L, TimeUnit.SECONDS)
            },
            EventAction("WaitForConnection") { runWithChecks() },
            EventAction("Disconnect") {
                Timber.i("onDisconnect")
                InactivityWatcher.cancel()
                // Utils.snackBar(this, "Disconnected from watch!", Snackbar.LENGTH_SHORT)
                val device = ProgressEvents.getPayload("Disconnect") as BluetoothDevice
                api().teardownConnection(device)
                val reconnectScheduler: ScheduledExecutorService =
                    Executors.newSingleThreadScheduledExecutor()
                reconnectScheduler.schedule({
                    runWithChecks()
                }, 3L, TimeUnit.SECONDS)
            },
            EventAction("ActionsPermissionsNotGranted") {
                Utils.snackBar(
                    this,
                    "Actions not granted...Cannot access the Actions screen..."
                )
            },
            EventAction("CalendarPermissionsNotGranted") {
                Utils.snackBar(
                    this,
                    "Calendar not granted...Cannot access the Actions screen..."
                )
            },
            EventAction("DeviceName") {
                val navView: BottomNavigationView = binding.navView
                navView.menu.findItem(R.id.navigation_events).isVisible =
                    WatchInfo.hasReminders
            },
            EventAction("WatchInitializationCompleted") { navigateHome() },
            EventAction("HomeTimeUpdated") {
                // This is really ugly, but I cannot update home time value
                // inside the HomeTime. Anybody knows why, let me know.
                val textView: HomeTime = findViewById(R.id.home_time)
                textView.update()
            })

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }

    private fun navigateHome() {
        if (findViewById<View>(R.id.nav_host_fragment_activity_gshock_screens) != null) {
            val navController =
                findNavController(R.id.nav_host_fragment_activity_gshock_screens)
            navController.navigate(R.id.navigation_home)
        }
    }

    private suspend fun waitForConnectionCached() {
        // Use this variable to control whether we should try to scan each time for the watch,
        // or reuse the last saved address. If set tto false, the connection is a bit slower,
        // but the app can connect to multiple watches without pressing "FORGET".
        // Also, auto-time-sync will work for multiple watches

        // Note: Consequently, we discovered that the Bluetooth scanning cannot be performed in the background,
        // so actions will fail. If this flag is true, no scanning will be performed.
        // Leave it to true.
        val reuseAddress = true

        var deviceAddress: String? = null

        if (reuseAddress) {
            val savedDeviceAddress = LocalDataStorage.get("LastDeviceAddress", "")
            if (api().validateBluetoothAddress(savedDeviceAddress)) {
                deviceAddress = savedDeviceAddress
            }
        }

        val deviceName = LocalDataStorage.get("LastDeviceName", "")

        // INZ new
        ProgressEvents.onNext("DeviceName", deviceName)

        api().waitForConnection(deviceAddress, deviceName)
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

        fun getLifecycleScope(): LifecycleCoroutineScope {
            return instance!!.lifecycleScope
        }
    }
}

