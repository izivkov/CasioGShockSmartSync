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
    private lateinit var bleManager: IGShockManager
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {

        if (!::bleManager.isInitialized) {
            bleManager = IGShockManager(context)
        }
    }

    private suspend fun connectToDevice(device: BluetoothDevice): ConnectionResult = try {
        bleManager.connect(device) { name, address ->
            WatchInfo.setNameAndModel(name.trimEnd('\u0000'))
            WatchInfo.setAddress(address)
            Timber.i("onConnected() end")
        }
        ConnectionResult.Success
    } catch (e: Exception) {
        ConnectionResult.Error(e.message ?: "Unknown error")
    }

    fun disconnect() {
        bleManager.release()
    }

    fun close() {
        bleManager.close()
    }

    private fun getConnectionState(): ConnectionState =
        runCatching {
            bleManager.connectionState
        }.onFailure { error ->
            Timber.e("Connection not initialized. Call Connection.init() before calling this function. ${error.message}")
        }.getOrDefault(ConnectionState.DISCONNECTED)

    fun isConnected(): Boolean =
        getConnectionState() == ConnectionState.CONNECTED

    fun isConnecting(): Boolean =
        getConnectionState() == ConnectionState.CONNECTING

    fun teardownConnection() {
        bleManager.release()
    }

    fun validateAddress(address: String?): Boolean =
        address != null && BluetoothAdapter.checkBluetoothAddress(address)

    @SuppressLint("NewApi")
    fun sendMessage(message: String) {
        MessageDispatcher.sendToWatch(message)
    }

    fun setDataCallback(dataCallback: IDataReceived) {
        bleManager.setDataCallback(dataCallback)
    }

    fun write(handle: GetSetMode, data: ByteArray) {
        scope.launch {
            bleManager.write(handle, data)
        }
    }

    fun isServiceSupported(handle: GetSetMode): Boolean =
        bleManager.isServiceSupported(handle)

    fun startConnection(context: Context, deviceId: String?) {
        scope.launch {
            if (deviceId.isNullOrEmpty()) {
                if (!isBluetoothEnabled(context)) {
                    ProgressEvents.onNext("ApiError", "Bluetooth is disabled")
                    return@launch
                }
                Timber.w("deviceId is null or empty. Listening for any device.")
            } else {
                connectToAddress(deviceId)
            }
        }
    }

    private suspend fun connectToAddress(address: String) {
        getDefaultAdapter()
            ?.getRemoteDevice(address)
            ?.let { device -> connectToDevice(device) }
            ?: ProgressEvents.onNext("ApiError", "Cannot obtain remote device")
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager =
            context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter.isEnabled
    }

    fun breakWait() {
        runCatching {
            bleManager.release()
        }.onFailure { error ->
            Timber.e("Connection not initialized. Call Connection.init() before calling this function. ${error.message}")
        }
    }
}

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}
