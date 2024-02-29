@file:Suppress("DEPRECATION")

package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.casio.MessageDispatcher

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

    fun isConnected() :Boolean {
        return bleManager?.connectionState == ConnectionState.CONNECTED
    }

    fun isConnecting(): Boolean {
        return bleManager?.connectionState == ConnectionState.CONNECTING
    }

    fun teardownConnection(device: BluetoothDevice) {
        bleManager?.release()
    }

    fun validateAddress(address: String?): Boolean {
        return address != null && BluetoothAdapter.checkBluetoothAddress(address)
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

    fun startConnection(context:Context, deviceId: String?, deviceName: String?) {
        scope.launch {
            var address = deviceId
            if (address == null) {
                stopBleScan()
                val devInfo = GShockScanner.scan(context).await()
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

    fun stopBleScan() {
        GShockScanner.cancelFlow()
    }

    fun breakWait() {
        bleManager?.release()
    }
}