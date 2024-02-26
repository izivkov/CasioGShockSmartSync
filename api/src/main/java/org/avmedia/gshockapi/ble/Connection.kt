@file:Suppress("DEPRECATION")

package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import timber.log.Timber

object Connection {

    private lateinit var bleManager: GShockManager

    @SuppressLint("MissingPermission")
    fun initialize(context: Context, deviceAddress: String) {
        val bluetoothAdapter: BluetoothAdapter? = getDefaultAdapter()
        // val deviceAddress = "00:11:22:33:44:55" // Example MAC address
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        bleManager = GShockManager(context = context, device = device as BluetoothDevice)
    }

    suspend fun connect() {
        val connection = bleManager.connect()
        println(connection)
    }

    fun disconnect() {
        bleManager.release()
    }

    // TODO
    fun isConnected() :Boolean {
        return true
    }

    fun teardownConnection(device: BluetoothDevice) {
        bleManager.release()
    }

    fun getDeviceId(): String? {
        return
    }

    fun validateAddress(address: String?): Boolean {
        return if (address != null && BluetoothAdapter.checkBluetoothAddress(address)) {
            true
        } else {
            Timber.e("Invalid Bluetooth Address")
            false
        }
    }
}