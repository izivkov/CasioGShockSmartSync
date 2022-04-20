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
import timber.log.Timber

data class BleScannerLocal(val context: Context) {
    lateinit var device: BluetoothDevice

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
        // .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .build()

    private var isScanning = false
    private val scanResults = mutableListOf<ScanResult>()

    @SuppressLint("MissingPermission")
    fun startConnection() {
        if (Connection.isConnected()) {
            return
        }
        var device: BluetoothDevice? = null
        val cachedDeviceAddr: String? = LocalDataStorage.get("cached device", context)
        if (cachedDeviceAddr != null) {
            device = bluetoothAdapter.getRemoteDevice(cachedDeviceAddr)
            this.device = device
        }

        if (device == null || device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
            if (isScanning) {
                return
            }
            bleScanner.startScan(createFilters(), scanSettings, scanCallback)
            isScanning = true
        } else {
            Connection.connect(device, context)
        }

        isScanning = true
    }

    private fun createFilters(): ArrayList<ScanFilter> {
        val filter = ScanFilter.Builder().setServiceUuid(
            ParcelUuid.fromString(CasioConstants.CASIO_SERVICE.toString())
        ).build()

        val filters = ArrayList<ScanFilter>()
        filters.add(filter)

        return filters
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            Timber.i ("onScanResult: result: $result")

            val cachedDevice = LocalDataStorage.get("cached device", context)
            if (cachedDevice == null || cachedDevice != result.device.address) {
                LocalDataStorage.put("cached device", result.device.address, context)
                Connection.connect(result.device, context)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }
}