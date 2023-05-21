/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 4:50 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import org.avmedia.gShockPhoneSync.databinding.ActivityMainBinding
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private val api = GShockAPI(this)
    private val testCrash = false

    var requestBluetooth =
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

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_gshock_screens)
        navView.setupWithNavController(navController)

        val deviceManager = DeviceManager

        createAppEventsSubscription()

        // This will run in the foreground, but not reliable. Do not use for now.
        // val intent = Intent(this, ForegroundService::class.java)
        // this.startService(intent)

        // ApiTest().run(this)
    }

    private fun run() {

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope.launch {
            Timber.i("=============== >>>> *** Waiting for connection... ***")
            waitForConnectionCached()
        }
    }

    private fun runWithChecks() {

        findNavController(R.id.nav_host_fragment_activity_gshock_screens)

        if (!isBluetoothEnabled()!!) {
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

    private fun isBluetoothEnabled(): Boolean? {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        return bluetoothAdapter?.isEnabled
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
        ProgressEvents.subscriber.start(this.javaClass.canonicalName,

            {
                when (it) {
                    ProgressEvents["ConnectionSetupComplete"] -> {
                        InactivityWatcher.start(this)
                    }

                    ProgressEvents["ConnectionFailed"] -> {
                        runWithChecks()
                    }

                    ProgressEvents["FineLocationPermissionNotGranted"] -> {
                        Utils.snackBar(
                            this,
                            "\"Fine Location\" Permission Not Granted! Clear the App's Cache to try again."
                        )
                        Timer("SettingUp", false).schedule(6000) {
                            finish()
                        }
                    }

                    ProgressEvents["FineLocationPermissionGranted"] -> {
                        Timber.i("FineLocationPermissionGranted")
                    }

                    ProgressEvents["ApiError"] -> {
                        Utils.snackBar(
                            this,
                            "ApiError! Something went wrong - Make sure the official G-Shock app in not running, to prevent interference."
                        )

                        val errorScheduler: ScheduledExecutorService =
                            Executors.newSingleThreadScheduledExecutor()
                        errorScheduler.schedule({
                            api().disconnect(this)
                        }, 3L, TimeUnit.SECONDS)
                    }

                    ProgressEvents["Disconnect"] -> {
                        Timber.i("onDisconnect")
                        InactivityWatcher.cancel()

                        Utils.snackBar(this, "Disconnected from watch!")
                        val event = ProgressEvents["Disconnect"]
                        val device = ProgressEvents["Disconnect"]?.payload as BluetoothDevice
                        api().teardownConnection(device)

                        val reconnectScheduler: ScheduledExecutorService =
                            Executors.newSingleThreadScheduledExecutor()
                        reconnectScheduler.schedule({
                            runWithChecks()
                        }, 3L, TimeUnit.SECONDS)
                    }

                    ProgressEvents["ActionsPermissionsNotGranted"] -> {
                        Utils.snackBar(
                            this, "Actions not granted...Cannot access the Actions screen..."
                        )
                        val navController =
                            findNavController(R.id.nav_host_fragment_activity_gshock_screens)
                        navController.navigate(R.id.navigation_home)
                    }

                    ProgressEvents["CalendarPermissionsNotGranted"] -> {
                        Utils.snackBar(
                            this, "Calendar not granted...Cannot access the Actions screen..."
                        )
                        val navController =
                            findNavController(R.id.nav_host_fragment_activity_gshock_screens)
                        navController.navigate(R.id.navigation_home)
                    }

                    ProgressEvents["WatchInitializationCompleted"] -> {
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
        val deviceAddress = when (LocalDataStorage.get ("ConnectionMode", "Single Watch", this)) {
            "Single Watch" -> LocalDataStorage.get("LastDeviceAddress", "", this)
            else -> ""
        }
        api().waitForConnection(deviceAddress)
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

