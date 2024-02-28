package org.avmedia.gshockapi.ble;

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.util.*

enum class ConnectionState {
    CONNECTING, CONNECTED, DISCONNECTED, DISCONNECTING
}

interface GSHock {
    suspend fun connect()
    fun release()
    fun setDataCallback(dataCallback: IDataReceived?)
    fun enableNotifications()
    suspend fun write(handle: Int, data: ByteArray)
    abstract fun setDevice(device: BluetoothDevice)
    abstract var connectionState: ConnectionState
}

class IGShockManager(
    context: Context,
) : GSHock by GShockManagerImpl(context)

private class GShockManagerImpl(
    context: Context,
) : BleManager(context), GSHock {

    private lateinit var readCharacteristic: BluetoothGattCharacteristic
    private lateinit var writeCharacteristic: BluetoothGattCharacteristic
    var dataReceivedCallback: IDataReceived? = null
    private lateinit var device: BluetoothDevice

    override var connectionState = ConnectionState.DISCONNECTED

    init {
        connectionObserver = ConnectionEventHandler()
    }

    override fun initialize() {
        super.initialize()
        ProgressEvents.onNext("BleManagerInitialized")

        setNotificationCallback(writeCharacteristic).with { _, data ->
            Timber.i("Received data from characteristic: ${data.value}")

            fun ByteArray.toHexString(): String =
                joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

            dataReceivedCallback?.dataReceived(data.value?.toHexString())
        }

        enableNotifications(writeCharacteristic).enqueue()
    }

    override fun setDevice(device: BluetoothDevice) {
        this.device = device
    }

    @SuppressLint("MissingPermission")
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect() = connect(device)
        .retry(3, 300)
        .useAutoConnect(false)
        .timeout(10000 * 60 * 1000)
        .enqueue()

    override fun release() {
        // Cancel all coroutines.
        scope.cancel()

        val wasConnected = isReady
        // If the device wasn't connected, it means that ConnectRequest was still pending.
        // Cancelling queue will initiate disconnecting automatically.
        cancelQueue()

        // If the device was connected, we have to disconnect manually.
        disconnect().enqueue()
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        dataReceivedCallback = dataCallback
    }

    override fun enableNotifications() {
        enableNotifications(writeCharacteristic)
            .fail { device, status ->
                // Handle failure to enable notifications
                Timber.i("Failed to enable notifications. Status: $status")
                ProgressEvents.onNext("ApiError")
            }
            .done { device ->
                // Notifications enabled successfully
                ProgressEvents.onNext("NotificationsEnabled", device)
            }
            .enqueue()
    }

    // Connection events
    inner class ConnectionEventHandler : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceConnecting!!!!!!")
            connectionState = ConnectionState.CONNECTING
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceConnected(device: BluetoothDevice) {
            ProgressEvents.onNext("ConnectionStarted")
            connectionState = ConnectionState.CONNECTED
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            ProgressEvents.onNext("ConnectionFailed")
            connectionState = ConnectionState.DISCONNECTED
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            Timber.i("$device onDeviceReady!!!!!!")
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceDisconnecting!!!!!!")
            connectionState = ConnectionState.DISCONNECTING
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            ProgressEvents.onNext("Disconnect", device)
            connectionState = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("NewApi", "MissingPermission")
    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        gatt.getService(CasioConstants.WATCH_FEATURES_SERVICE_UUID)?.apply {
            readCharacteristic = getCharacteristic(
                CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID,
            )

            writeCharacteristic = getCharacteristic(
                CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID,
            )
            ProgressEvents.onNext("ConnectionSetupComplete", gatt.device)

            // new
            ProgressEvents.onNext("DeviceName", gatt.device.name)
            ProgressEvents.onNext("DeviceAddress", gatt.device.address)

            return true
        }
        return false
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

    override suspend fun write(handle: Int, data: ByteArray) {

        val characteristic = if (handle == 0xC) readCharacteristic else writeCharacteristic
        writeCharacteristic(
            characteristic,
            data,
            getWriteType(characteristic)
        ).await()
    }

    private fun getWriteType(characteristic: BluetoothGattCharacteristic): Int {
        fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
            properties and property != 0

        fun BluetoothGattCharacteristic.isReadable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

        fun BluetoothGattCharacteristic.isWritable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

        fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

        fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

        fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            else -> {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
        }

        return writeType
    }

    companion object {

        object CasioConstants {
            val WATCH_FEATURES_SERVICE_UUID: UUID =
                UUID.fromString("26eb000d-b012-49a8-b1f8-394fb2032b0f")

            val CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("26eb002c-b012-49a8-b1f8-394fb2032b0f")
            val CASIO_ALL_FEATURES_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("26eb002d-b012-49a8-b1f8-394fb2032b0f")
            val CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("26eb0023-b012-49a8-b1f8-394fb2032b0f")
            val CASIO_CONVOY_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("26eb0024-b012-49a8-b1f8-394fb2032b0f")

            val CASIO_NOTIFICATION_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("26eb0030-b012-49a8-b1f8-394fb2032b0f")

            val CASIO_GET_DEVICE_NAME: UUID =
                UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb") // returns 0x43 41 53 49 4F 20 47 57 2D 42 35 36 30 30 00 00 (CASIO GW-B5600)
            val CASIO_APPEARANCE: UUID = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")
            val TX_POWER_LEVEL_CHARACTERISTIC_UUID: UUID =
                UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")

            val SERIAL_NUMBER_STRING: UUID =
                UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
        }
    }
}



