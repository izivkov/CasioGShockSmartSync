/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-09, 10:05 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-09, 10:05 a.m.
 */

package org.avmedia.gShockPhoneSync.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.appcompat.app.AppCompatActivity
import org.avmedia.gShockPhoneSync.casioB5600.CasioConstants
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import timber.log.Timber

data class BleScannerLocal(val context: Context) {
    lateinit var device: BluetoothDevice
    private val cacheDevice = false

    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
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

    @SuppressLint("MissingPermission")
    fun startConnection() {
        if (Utils.isDebugMode()) {
            return
        }

        if (Connection.isConnected()) {
            return
        }
        var device: BluetoothDevice? = null
        var cachedDeviceAddr: String? = null
        if (cacheDevice) {
            cachedDeviceAddr = LocalDataStorage.get("cached device", null, context)
        }

        if (cachedDeviceAddr != null) {
            device = bluetoothAdapter.getRemoteDevice(cachedDeviceAddr)
            this.device = device
        }

        if (device == null || device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
            if (isScanning) {
                return
            }
            scanSettings.describeContents()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || bleScanner == null) {
                Utils.snackBar(
                    context,
                    "Bluetooth not available. Please turn on Bluetooth and restart the app."
                )
                return
            }
            bleScanner.startScan(createFilters(), scanSettings, scanCallback)
            isScanning = true
        } else {
            Connection.connect(device, context)
        }

        isScanning = true
    }

/*
I/BleExtensionsKt: Service 00001801-0000-1000-8000-00805f9b34fb
Characteristics:
|--
I/BleExtensionsKt: Service 00001800-0000-1000-8000-00805f9b34fb
Characteristics:
|--00002a00-0000-1000-8000-00805f9b34fb: READABLE
|--00002a01-0000-1000-8000-00805f9b34fb: READABLE
I/BleExtensionsKt: Service 00001804-0000-1000-8000-00805f9b34fb
Characteristics:
|--00002a07-0000-1000-8000-00805f9b34fb: READABLE
I/BleExtensionsKt: Service 26eb000d-b012-49a8-b1f8-394fb2032b0f
Characteristics:
|--26eb002c-b012-49a8-b1f8-394fb2032b0f: WRITABLE WITHOUT RESPONSE
|--26eb002d-b012-49a8-b1f8-394fb2032b0f: WRITABLE, NOTIFIABLE
|------00002902-0000-1000-8000-00805f9b34fb: EMPTY
|--26eb0023-b012-49a8-b1f8-394fb2032b0f: WRITABLE, NOTIFIABLE
|------00002902-0000-1000-8000-00805f9b34fb: EMPTY
|--26eb0024-b012-49a8-b1f8-394fb2032b0f: WRITABLE WITHOUT RESPONSE, NOTIFIABLE
|------00002902-0000-1000-8000-00805f9b34fb: EMPTY
*/

    private fun createFilters(): ArrayList<ScanFilter> {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(CasioConstants.CASIO_SERVICE.toString()))
            .build()

        val filters = ArrayList<ScanFilter>()
        filters.add(filter)
        return filters
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            stopBleScan()

            if (cacheDevice) {
                val cachedDevice = LocalDataStorage.get("cached device", null, context)
                if (cachedDevice == null || cachedDevice != result.device.address) {
                    LocalDataStorage.put("cached device", result.device.address, context)
                }
            }

            Connection.connect(result.device, context)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }
}