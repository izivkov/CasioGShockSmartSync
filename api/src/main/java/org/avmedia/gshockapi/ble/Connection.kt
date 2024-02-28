@file:Suppress("DEPRECATION")

package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.casio.MessageDispatcher
import timber.log.Timber

object Connection {

    private  var bleManager: IGShockManager? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private suspend fun connect() {

        fun onConnected (name: String, address: String) {
            WatchInfo.setNameAndModel(name.trimEnd('\u0000'))
            WatchInfo.setAddress(address)
        }

        bleManager?.connect(::onConnected)
    }

    fun disconnect() {
        bleManager?.release()
    }

    // TODO
    fun isConnected() :Boolean {
        return bleManager?.connectionState == ConnectionState.CONNECTED
    }

    fun isConnecting(): Boolean {
        return bleManager?.connectionState == ConnectionState.CONNECTING
    }

    fun teardownConnection(device: BluetoothDevice) {
        bleManager?.release()
    }

    fun getDeviceId(): String? {
        return null
    }

    fun validateAddress(address: String?): Boolean {
        return if (address != null && BluetoothAdapter.checkBluetoothAddress(address)) {
            true
        } else {
            Timber.e("Invalid Bluetooth Address")
            false
        }
    }

    @SuppressLint("NewApi")
    fun sendMessage(message: String) {
        MessageDispatcher.sendToWatch(message)
    }

    fun setDataCallback(dataCallback: IDataReceived) {
        bleManager?.setDataCallback(dataCallback)
    }

    fun write(handle: Int, data: ByteArray) {
        scope.launch {
            bleManager?.write(handle, data)
        }
    }

    fun isBluetoothEnabled(): Boolean {

        // TODO: Needs implementation
        return true
    }

    fun stopBleScan() {
        // TODO("Not yet implemented")
    }

    fun startConnection(context:Context, deviceId: String?, deviceName: String?) {
        scope.launch {
            var address = deviceId
            if (address == null) {
                val devInfo = BleScannerGShock.scan(context).await()
                address = devInfo.address
            }
            val bluetoothAdapter: BluetoothAdapter? = getDefaultAdapter()
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)

            if (bleManager == null) {
                bleManager = IGShockManager(context)
            }
            bleManager?.setDevice(device as BluetoothDevice)
            connect()
        }
    }
    fun breakWait() {
        // TODO("Not yet implemented")
    }
}