package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.casio.CasioConstants
import timber.log.Timber
import java.util.UUID

enum class ConnectionState {
    CONNECTING, CONNECTED, DISCONNECTED, DISCONNECTING
}

typealias onConnectedType = (String, String) -> Unit

enum class GetSetMode {
    GET,
    SET,
    NOTIFY
}

interface GSHock {
    suspend fun connect(device: BluetoothDevice, onConnected: (String, String) -> Unit)
    fun release()
    fun close()
    fun setDataCallback(dataCallback: IDataReceived?)
    fun enableNotifications()
    var connectionState: ConnectionState
    suspend fun write(handle: GetSetMode, data: ByteArray)
    fun isServiceSupported(handle: GetSetMode): Boolean
}

class IGShockManager(
    context: Context,
) : GSHock by GShockManagerImpl(context)

private class GShockManagerImpl(
    context: Context,
) : BleManager(context), GSHock {

    private var readCharacteristicHolder: BluetoothGattCharacteristic? = null
    private var writeCharacteristicHolder: BluetoothGattCharacteristic? = null
    private var writeCharacteristicHolderNotifications: BluetoothGattCharacteristic? = null

    var dataReceivedCallback: IDataReceived? = null
    private lateinit var device: BluetoothDevice
    override var connectionState = ConnectionState.DISCONNECTED
    private lateinit var onConnected: onConnectedType

    init {
        connectionObserver = ConnectionEventHandler()
    }

    override fun initialize() {
        super.initialize()
        writeCharacteristicHolder?.let { characteristic ->
            setNotificationCallback(characteristic).with { _, data ->
                val hexData = data.value?.joinToString(separator = " ", prefix = "0x") {
                    String.format("%02X", it)
                }
                dataReceivedCallback?.dataReceived(hexData)
            }
            enableNotifications(characteristic).enqueue()
        }
        ProgressEvents.onNext("BleManagerInitialized")
    }

    // Characteristic map
    // Store all characteristic UUIDs
    private data class CharacteristicInfo(
        val uuid: UUID,
        val properties: Int
    )

    private val characteristicUUIDs = mutableMapOf<String, CharacteristicInfo>()

    // Initialize characteristics when service is discovered
    private fun initCharacteristicsMap(gatt: BluetoothGatt) {
        gatt.services.forEach { service ->
            service.characteristics.forEach { char ->
                characteristicUUIDs[char.uuid.toString()] = CharacteristicInfo(
                    uuid = char.uuid,
                    properties = char.properties
                )
            }
        }

        Timber.i("Found ${characteristicUUIDs.size} characteristics:")
        characteristicUUIDs.forEach { (key, info) ->
            Timber.i("UUID: $key, Properties: ${propsToString(info.properties)}")
        }
    }
    // End Characteristic map

    @SuppressLint("MissingPermission")
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect(device: BluetoothDevice, onConnected: onConnectedType) {
        this.onConnected = onConnected

        connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .done {
                requestMtu(256).enqueue()
                Timber.d("BLE", "Connected with autoConnect!")
            }
            .fail { _, status -> Timber.d("BLE", "Failed with status $status") }
            .enqueue()
    }

    override fun close() {
        super.close()
    }

    override fun release() {
        connectionState = ConnectionState.DISCONNECTING

        scope.cancel()
        cancelQueue()

        // Close GATT resources
        close()

        // Clear all characteristics
        readCharacteristicHolder = null
        writeCharacteristicHolder = null
        writeCharacteristicHolderNotifications = null
        characteristicUUIDs.clear()

        if (isReady) {
            disconnect().enqueue()
        }
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        dataReceivedCallback = dataCallback
    }

    override fun enableNotifications() {
        enableNotifications(writeCharacteristicHolder)
            .fail { _, status ->
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

        @SuppressLint("MissingPermission")
        override fun onDeviceReady(device: BluetoothDevice) {
            Timber.i("$device DeviceReady!!!!!!")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                device.createBond()
            }

            if (device.name == null) {
                ProgressEvents.onNext("ApiError", "Cannot obtain device name")
                return
            }

            val name = device.name ?: "CASIO"
            onConnected(name, device.address)

            // inform the caller that we have connected
            ProgressEvents.onNext("DeviceName", name)
            ProgressEvents.onNext("DeviceAddress", device.address)
            ProgressEvents.onNext("ConnectionSetupComplete", device)

            Timber.i("onDeviceReady: Sent all messages.")
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceDisconnecting!!!!!!")
            connectionState = ConnectionState.DISCONNECTING
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            ProgressEvents.onNext("Disconnect", device)

            Timber.d("Device disconnected with reason: $reason")
            when (reason) {
                ConnectionObserver.REASON_NOT_SUPPORTED -> Timber.e("Device not supported")
                ConnectionObserver.REASON_TERMINATE_LOCAL_HOST -> Timber.d("Terminated by local host")
                ConnectionObserver.REASON_TERMINATE_PEER_USER -> Timber.d("Terminated by peer device")
                ConnectionObserver.REASON_LINK_LOSS -> Timber.w("Connection lost")
            }

            connectionState = ConnectionState.DISCONNECTED

            // Add cleanup
            readCharacteristicHolder = null
            writeCharacteristicHolder = null
            writeCharacteristicHolderNotifications = null
            characteristicUUIDs.clear()

            // Force garbage collection of BLE resources
            System.gc()
        }
    }

    @SuppressLint("NewApi", "MissingPermission")
    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        initCharacteristicsMap(gatt)

        gatt.getService(CasioConstants.WATCH_FEATURES_SERVICE_UUID)?.apply {
            readCharacteristicHolder = getCharacteristic(
                CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID,
            )
            writeCharacteristicHolder = getCharacteristic(
                CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID,
            )
            if (findCharacteristic(gatt, CasioConstants.CASIO_NOTIFICATION_CHARACTERISTIC_UUID)) {
                writeCharacteristicHolderNotifications = getCharacteristic(
                    CasioConstants.CASIO_NOTIFICATION_CHARACTERISTIC_UUID,
                )
            }
            return true
        }
        return false
    }

    private fun findCharacteristic(gatt: BluetoothGatt, uuid: UUID): Boolean {
        gatt.services.forEach { service ->
            service.characteristics.forEach { char ->
                val properties = propsToString(char.properties)
                if (char.uuid == uuid) {
                    Timber.i("Found characteristic: ${char.uuid} ($properties)")
                    return true
                }
            }
        }

        return false
    }

    private fun printCharacteristics(gatt: BluetoothGatt) {
        gatt.services.forEach { service ->
            println("Service: ${service.uuid}")
            service.characteristics.forEach { char ->
                val properties = propsToString(char.properties)
                println("  └─ Characteristic: ${char.uuid} ($properties)")
            }
        }
    }

    private fun propsToString(properties: Int): String {
        val props = mutableListOf<String>()
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("WRITE_NO_RESPONSE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("INDICATE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) props.add("SIGNED_WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) props.add("BROADCAST")
        return props.joinToString(" | ")
    }

    /* GW-5600
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

    /* DW-H5600
  Service: 00001801-0000-1000-8000-00805f9b34fb
  Service: 00001800-0000-1000-8000-00805f9b34fb
    └─ Characteristic: 00002a00-0000-1000-8000-00805f9b34fb (READ)
    └─ Characteristic: 00002a01-0000-1000-8000-00805f9b34fb (READ)
    └─ Characteristic: 00002a04-0000-1000-8000-00805f9b34fb (READ)
    └─ Characteristic: 00002aa6-0000-1000-8000-00805f9b34fb (READ)
  Service: 00001804-0000-1000-8000-00805f9b34fb
    └─ Characteristic: 00002a07-0000-1000-8000-00805f9b34fb (READ)
  Service: 26eb000d-b012-49a8-b1f8-394fb2032b0f
    └─ Characteristic: 26eb0023-b012-49a8-b1f8-394fb2032b0f (WRITE | NOTIFY)
    └─ Characteristic: 26eb0024-b012-49a8-b1f8-394fb2032b0f (WRITE_NO_RESPONSE | NOTIFY)
    └─ Characteristic: 26eb002c-b012-49a8-b1f8-394fb2032b0f (WRITE_NO_RESPONSE)
    └─ Characteristic: 26eb002d-b012-49a8-b1f8-394fb2032b0f (WRITE | NOTIFY)
    └─ Characteristic: 26eb0030-b012-49a8-b1f8-394fb2032b0f (WRITE_NO_RESPONSE) // unique to DW-H5600
     */

    override fun isServiceSupported(handle: GetSetMode): Boolean {
        val uuid = when (handle) {
            GetSetMode.GET -> CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID
            GetSetMode.SET -> CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID
            GetSetMode.NOTIFY -> CasioConstants.CASIO_NOTIFICATION_CHARACTERISTIC_UUID
            else -> return false
        }

        val isSupported = characteristicUUIDs.containsKey(uuid.toString())
        return isSupported
    }

    override suspend fun write(handle: GetSetMode, data: ByteArray) {
        if (characteristicUUIDs.isEmpty()) {
            ProgressEvents.onNext("ApiError", "Not connected to watch")
            disconnect()
            return
        }

        if (!isServiceSupported(handle)) {
            Timber.e("ApiError", "${handle.name.lowercase()} feature not supported")
            return
        }

        val characteristic = when (handle) {
            GetSetMode.GET -> readCharacteristicHolder
            GetSetMode.SET -> writeCharacteristicHolder
            GetSetMode.NOTIFY -> writeCharacteristicHolderNotifications
            else -> {
                ProgressEvents.onNext("ApiError", "Invalid handle: $handle")
                disconnect()
                return
            }
        }

        val writeType =
            if (handle == GetSetMode.GET || handle == GetSetMode.NOTIFY)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        try {
            writeCharacteristic(
                characteristic,
                data,
                writeType
            ).enqueue()
        } catch (e: Exception) {
            Timber.e(e, "Error writing to characteristic ${characteristic?.uuid}")
            ProgressEvents.onNext("ApiError", "Failed to write data: ${e.message}")
            disconnect()
        }
    }
}



