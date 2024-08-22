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
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.casio.MessageDispatcher
import timber.log.Timber

object Connection {

    private var bleManager: IGShockManager? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private suspend fun connect(device: BluetoothDevice) {

        fun onConnected(name: String, address: String) {
            WatchInfo.setNameAndModel(name.trimEnd('\u0000'))
            WatchInfo.setAddress(address)
            Timber.i("onConnected() end")
        }

        bleManager?.connect(device, ::onConnected)
    }

    fun disconnect() {
        bleManager?.release()

    }

    fun isConnected(): Boolean {
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

    fun write(handle: GET_SET_MODE, data: ByteArray) {
        scope.launch {
            bleManager?.write(handle, data)
        }
    }

    fun startConnection(context: Context, deviceId: String?, deviceName: String?) {
        scope.launch {
            if (deviceId.isNullOrEmpty()) {
                stopBleScan()

                fun onScanCompleted(deviceInfo: GShockScanner.DeviceInfo?) {
                    scope.launch {
                        connect(deviceInfo?.address, context)
                    }
                }

                GShockScanner.scan(context, ::onScanCompleted)
            } else {
                connect(deviceId, context)
            }
        }
    }

    private suspend fun connect(address: String?, context: Context) {
        if (address.isNullOrEmpty()) {
            return
        }
        val bluetoothAdapter: BluetoothAdapter? = getDefaultAdapter()
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            ProgressEvents.onNext("ApiError", "Cannot obtain remote device")
            return
        }

        if (bleManager == null) {
            bleManager = IGShockManager(context)
        }
        connect(device)
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothAdapter: BluetoothAdapter by lazy {
            val bluetoothManager =
                context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }
        return bluetoothAdapter.isEnabled
    }

    fun stopBleScan() {
        GShockScanner.cancelFlow()
    }

    fun breakWait() {
        bleManager?.release()
    }
}