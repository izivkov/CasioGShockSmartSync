@file:Suppress("DEPRECATION")

package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.casio.MessageDispatcher
import timber.log.Timber

object Connection {
    @Volatile
    private var bleManager: IGShockManager? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)
    fun isInitialized(): Boolean = bleManager != null

    @Synchronized
    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }

        if (bleManager == null) {
            bleManager = IGShockManager(appContext)
            Timber.i("Created BLE manager for connection lifecycle")
        }
    }

    private fun requireBleManager(): IGShockManager =
        bleManager ?: synchronized(this) {
            bleManager ?: run {
                check(::appContext.isInitialized) { "Connection.init(context) must be called first" }
                IGShockManager(appContext).also { bleManager = it }
            }
        }

    private fun releaseBleManager() {
        val manager = synchronized(this) {
            bleManager.also { bleManager = null }
        }
        if (manager != null) Timber.i("Releasing BLE manager; next connection will create a fresh manager")
        manager?.release()
    }

    private suspend fun connectToDevice(device: BluetoothDevice): ConnectionResult = try {
        requireBleManager().connect(device) { name, address ->
            WatchInfo.setNameAndModel(name.trimEnd('\u0000'))
            WatchInfo.setAddress(address)
            Timber.i("onConnected() end")
        }
        ConnectionResult.Success
    } catch (e: Exception) {
        ConnectionResult.Error(e.message ?: "Unknown error")
    }

    fun disconnect() {
        releaseBleManager()
    }

    fun close() {
        releaseBleManager()
    }

    private fun getConnectionState(): ConnectionState =
        runCatching {
            bleManager?.connectionState ?: ConnectionState.DISCONNECTED
        }.onFailure { error ->
            Timber.e("Connection not initialized. Call Connection.init() before calling this function. ${error.message}")
        }.getOrDefault(ConnectionState.DISCONNECTED)

    fun isConnected(): Boolean {
        return getConnectionState() == ConnectionState.CONNECTED
    }

    fun isConnecting(): Boolean {
        return getConnectionState() == ConnectionState.CONNECTING
    }

    /**
     * Checks Android's own Bluetooth stack for whether the device we believe we're
     * connected to is actually still connected at the OS level. This is
     * authoritative when it can be checked - if the OS says "not connected", our
     * internal flag is definitely wrong, regardless of how recently it changed, so
     * a genuinely healthy but long-idle connection is never affected by this check.
     * Returns null when we can't get a clean answer (no tracked address yet,
     * missing permission, adapter unavailable), so the caller can fall back to the
     * time-based heuristic instead.
     */
    @SuppressLint("MissingPermission")
    private fun isTrackedDeviceConnectedAtOsLevel(): Boolean? {
        val address = WatchInfo.getAddress()
        if (address.isEmpty() || !::appContext.isInitialized) return null

        return runCatching {
            val bluetoothManager =
                appContext.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                .any { it.address == address }
        }.getOrNull()
    }

    fun teardownConnection() {
        releaseBleManager()
    }

    fun validateAddress(address: String?): Boolean =
        address != null && BluetoothAdapter.checkBluetoothAddress(address)

    @SuppressLint("NewApi")
    fun sendMessage(message: String) {
        MessageDispatcher.sendToWatch(message)
    }

    fun setDataCallback(dataCallback: IDataReceived) {
        requireBleManager().setDataCallback(dataCallback)
    }

    fun write(handle: GetSetMode, data: ByteArray) {
        scope.launch {
            val manager = bleManager
            if (manager == null) {
                ProgressEvents.onNext("ApiError", "Connection manager is not initialized")
            } else {
                manager.write(handle, data)
            }
        }
    }

    fun isServiceSupported(handle: GetSetMode): Boolean =
        bleManager?.isServiceSupported(handle) == true

    fun startConnection(context: Context, deviceId: String?) {
        ProgressEvents.onNext("ConnectionStarted")
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
            ?.let { device ->
                when (val result = connectToDevice(device)) {
                    is ConnectionResult.Error ->
                        ProgressEvents.onNext("ApiError", "Connection error: ${result.message}")
                    ConnectionResult.Success -> Unit
                }
            }
            ?: ProgressEvents.onNext("ApiError", "Cannot obtain remote device")
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager =
            context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter.isEnabled
    }

    fun breakWait() {
        releaseBleManager()
    }

    fun scan(
        context: Context,
        isBluetoothOn: () -> Boolean,
        filter: (DeviceInfo) -> Boolean,
        onDeviceFound: (DeviceInfo) -> Unit
    ) {
        GShockScanner.startScan(context, isBluetoothOn, filter, onDeviceFound)
    }

    fun stopScan() {
        GShockScanner.stopScan()
    }

    fun startFallbackScan(
        context: Context,
        addresses: List<String>,
        pendingIntent: android.app.PendingIntent
    ) {
        GShockScanner.startFallbackScan(context, addresses, pendingIntent)
    }
}

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}
