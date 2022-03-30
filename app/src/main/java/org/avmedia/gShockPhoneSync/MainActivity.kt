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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ble.Connection.connect
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.casioB5600.CasioConstants
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.casioB5600.WatchDataCollector
import org.avmedia.gShockPhoneSync.databinding.ActivityMainBinding
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataListener
import org.jetbrains.anko.alert
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity() {

    lateinit var device: BluetoothDevice
    private lateinit var binding: ActivityMainBinding

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var isScanning = false

    private val scanResults = mutableListOf<ScanResult>()

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        ScreenSelector.add("connect screen", binding.connectionLayout)
        ScreenSelector.add("g-shock screen", binding.mainLayout)

        ScreenSelector.showScreen("connect screen")

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_gshock_screens)
        navView.setupWithNavController(navController)

        startConnection()
        Connection.init(this)
        WatchDataListener.init()
    }

    override fun onResume() {
        super.onResume()
        createAppEventsSubscription()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        InactivityWatcher.resetTimer(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startConnection()
                }
            }
        }
    }

    /*******************************************
     * Private functions
     *******************************************/

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        } else {
            var device: BluetoothDevice? = null
            val cachedDeviceAddr: String? = LocalDataStorage.get("cached device", this)
            if (cachedDeviceAddr != null) {
                device = bluetoothAdapter.getRemoteDevice(cachedDeviceAddr)
                this.device = device
            }

            if (device == null || device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                bleScanner.startScan(createFilters(), scanSettings, scanCallback)
            } else {
                connect(device, this)
            }

            isScanning = true
        }
    }

    private fun createFilters(): ArrayList<ScanFilter> {
        val filter = ScanFilter.Builder().setServiceUuid(
            ParcelUuid.fromString(CasioConstants.CASIO_SERVICE.toString())
        ).build()

        val filters = ArrayList<ScanFilter>()
        filters.add(filter)

        return filters
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title = "Location permission required"
                message = "Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices."
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }

    /*******************************************
     * Callback bodies
     *******************************************/

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

//            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
//
//            if (indexQuery != -1) { // A scan result already exists with the same address
//                scanResults[indexQuery] = result
//                scanResultAdapter.notifyItemChanged(indexQuery)
//            } else {
//                with(result.device) {
//                    Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
//                    connect(result.device, this@MainActivity)
//                }
//                scanResults.add(result)
//                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
//            }

            if (LocalDataStorage.get("cached device", this@MainActivity) == null) {
                LocalDataStorage.put("cached device", result.device.address, this@MainActivity)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
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
                        InactivityWatcher.start(this)
                    }
                    ProgressEvents.Events.Disconnect -> {
                        Timber.i("onDisconnect")
                        InactivityWatcher.cancel()

                        Utils.toast(this, "Disconnected from watch!")
                        val device = ProgressEvents.Events.Disconnect.payload as BluetoothDevice
                        Connection.teardownConnection(device)

                        // restart after 5 seconds
                        val reconnectScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
                        reconnectScheduler.schedule({
                            startConnection()
                        }, 5L, TimeUnit.SECONDS)
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

    /*******************************************
     * Extension functions
     *******************************************/

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }
}
