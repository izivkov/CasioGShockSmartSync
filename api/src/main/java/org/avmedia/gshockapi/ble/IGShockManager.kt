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

typealias OnConnectedType = (String, String) -> Unit

enum class GetSetMode {
    GET,
    SET,
    NOTIFY,
    SP_REQUEST,
    SP_DATA
}

interface GShock {
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
) : GShock by GShockManagerImpl(context)

private class GShockManagerImpl(
    context: Context,
) : BleManager(context), GShock {

    private var readCharacteristicHolder: BluetoothGattCharacteristic? = null
    private var writeCharacteristicHolder: BluetoothGattCharacteristic? = null
    private var writeCharacteristicHolderNotifications: BluetoothGattCharacteristic? = null
    private var writeCharacteristicHolderSPRequest: BluetoothGattCharacteristic? = null
    private var writeCharacteristicHolderSPData: BluetoothGattCharacteristic? = null

    var dataReceivedCallback: IDataReceived? = null
    private lateinit var device: BluetoothDevice
    override var connectionState = ConnectionState.DISCONNECTED
    private lateinit var onConnected: OnConnectedType

    init {
        connectionObserver = ConnectionEventHandler()
    }

    override fun initialize() {
        super.initialize()
        ProgressEvents.onNext("BleManagerInitialized")
    }

    // Characteristic map — stores all discovered characteristic UUIDs and properties
    private data class CharacteristicInfo(
        val uuid: UUID,
        val properties: Int
    )

    private val characteristicUUIDs = mutableMapOf<String, CharacteristicInfo>()

    private fun initCharacteristicsMap(gatt: BluetoothGatt) {
        gatt.services.forEach { service ->
            service.characteristics.forEach { char ->
                characteristicUUIDs[char.uuid.toString()] = CharacteristicInfo(
                    uuid = char.uuid,
                    properties = char.properties
                )
            }
        }

        Timber.d("Found ${characteristicUUIDs.size} characteristics:")
        characteristicUUIDs.forEach { (key, info) ->
            Timber.i("UUID: $key, Properties: ${propsToString(info.properties)}")
        }

        // Subscribe to notifications on every characteristic that supports them.
        // Mirrors the Python subscribe-all approach — no per-model whitelists needed.
        gatt.services.forEach { service ->
            service.characteristics.forEach { char ->
                val hasNotify = char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val hasIndicate = char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                if (hasNotify || hasIndicate) {
                    setNotificationCallback(char).with { _, data ->
                        val hexData = data.value?.joinToString(separator = " ", prefix = "0x") {
                            String.format("%02X", it)
                        }
                        Timber.d("Notification from ${char.uuid}: $hexData")
                        dataReceivedCallback?.dataReceived(hexData)
                    }
                    enableNotifications(char)
                        .fail { _, status ->
                            Timber.d("Failed to enable notifications for ${char.uuid}: $status")
                        }
                        .done {
                            Timber.d("Subscribed to notifications: ${char.uuid}")
                        }
                        .enqueue()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect(device: BluetoothDevice, onConnected: OnConnectedType) {
        this.onConnected = onConnected

        connect(device)
            .retry(3, 100)
            .useAutoConnect(true)
            .done {
                requestMtu(256).enqueue()
                Timber.d("Connected with autoConnect!")
            }
            .fail { _, status -> Timber.d("Failed with status $status") }
            .enqueue()
    }

    override fun close() {
        super.close()
    }

    override fun release() {
        connectionState = ConnectionState.DISCONNECTING

        scope.cancel()
        cancelQueue()

        close()

        readCharacteristicHolder = null
        writeCharacteristicHolder = null
        writeCharacteristicHolderNotifications = null
        writeCharacteristicHolderSPRequest = null
        writeCharacteristicHolderSPData = null
        characteristicUUIDs.clear()

        if (isReady) {
            disconnect().enqueue()
        }
    }

    override fun setDataCallback(dataCallback: IDataReceived?) {
        dataReceivedCallback = dataCallback
    }

    override fun enableNotifications() {
        // No-op: notifications are now enabled for all notifiable characteristics
        // in initialize(). Kept for interface compatibility.
    }

    // Connection events
    inner class ConnectionEventHandler : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceConnecting")
            connectionState = ConnectionState.CONNECTING
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceConnected(device: BluetoothDevice) {
            Timber.i("$device onDeviceConnected — waiting for service discovery")
            ProgressEvents.onNext("ConnectionStarted")
            connectionState = ConnectionState.CONNECTED
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Timber.w("$device onDeviceFailedToConnect, reason=$reason")
            ProgressEvents.onNext("ConnectionFailed")
            connectionState = ConnectionState.DISCONNECTED
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceReady(device: BluetoothDevice) {
            Timber.i("$device DeviceReady")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                device.bondState != BluetoothDevice.BOND_BONDED
            ) {
                device.createBond()
            }

            if (device.name == null) {
                ProgressEvents.onNext("ApiError", "Cannot obtain device name")
                return
            }

            val name = device.name ?: "CASIO"
            onConnected(name, device.address)

            ProgressEvents.onNext("DeviceName", name)
            ProgressEvents.onNext("DeviceAddress", device.address)
            ProgressEvents.onNext("ConnectionSetupComplete", device)

            Timber.i("onDeviceReady: complete")
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Timber.i("$device onDeviceDisconnecting")
            connectionState = ConnectionState.DISCONNECTING
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            ProgressEvents.onNext("Disconnect", device)

            Timber.d("Device disconnected with reason: $reason")
            when (reason) {
                ConnectionObserver.REASON_NOT_SUPPORTED -> Timber.e("Device not supported")
                ConnectionObserver.REASON_TERMINATE_LOCAL_HOST -> Timber.d("Terminated by local host")
                ConnectionObserver.REASON_TERMINATE_PEER_USER -> Timber.d("Terminated by peer device")
                ConnectionObserver.REASON_CANCELLED,
                ConnectionObserver.REASON_SUCCESS,
                ConnectionObserver.REASON_TIMEOUT,
                ConnectionObserver.REASON_LINK_LOSS,
                ConnectionObserver.REASON_UNKNOWN -> {
                    Timber.d("Standard disconnection: $reason")
                }
            }
            connectionState = ConnectionState.DISCONNECTED

            readCharacteristicHolder = null
            writeCharacteristicHolder = null
            writeCharacteristicHolderNotifications = null
            writeCharacteristicHolderSPRequest = null
            writeCharacteristicHolderSPData = null
            characteristicUUIDs.clear()

            System.gc()
        }
    }

    @SuppressLint("NewApi", "MissingPermission")
    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        Timber.i("isRequiredServiceSupported called, services found: ${gatt.services.size}")
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
            if (findCharacteristic(gatt, CasioConstants.CASIO_SET_CONFIGURATION_CHARACTERISTIC_UUID)) {
                writeCharacteristicHolderSPRequest = getCharacteristic(
                    CasioConstants.CASIO_SET_CONFIGURATION_CHARACTERISTIC_UUID,
                )
            }
            if (findCharacteristic(gatt, CasioConstants.CASIO_GET_CONFIGURATION_CHARACTERISTIC_UUID)) {
                writeCharacteristicHolderSPData = getCharacteristic(
                    CasioConstants.CASIO_GET_CONFIGURATION_CHARACTERISTIC_UUID,
                )
            }
            Timber.i("isRequiredServiceSupported returning true")
            return true
        }
        Timber.w("isRequiredServiceSupported returning false — WATCH_FEATURES_SERVICE_UUID not found")
        return false
    }

    private fun findCharacteristic(gatt: BluetoothGatt, uuid: UUID): Boolean {
        gatt.services.forEach { service ->
            service.characteristics.forEach { char ->
                if (char.uuid == uuid) {
                    Timber.i("Found characteristic: ${char.uuid} (${propsToString(char.properties)})")
                    return true
                }
            }
        }
        return false
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

    /* GW-BX5600
  Service: 26eb000d-b012-49a8-b1f8-394fb2032b0f
    └─ Characteristic: 26eb002c-b012-49a8-b1f8-394fb2032b0f (WRITE_NO_RESPONSE)         handle 0x000c
    └─ Characteristic: 26eb002d-b012-49a8-b1f8-394fb2032b0f (WRITE | NOTIFY)            handle 0x000e
    └─ Characteristic: 26eb0023-b012-49a8-b1f8-394fb2032b0f (WRITE | NOTIFY)            handle 0x0011
    └─ Characteristic: 26eb0024-b012-49a8-b1f8-394fb2032b0f (WRITE_NO_RESPONSE | NOTIFY) handle 0x0014
    └─ Characteristic: 26eb002e-b012-49a8-b1f8-394fb2032b0f (WRITE_NO_RESPONSE)         handle 0x0017  SP_REQUEST
    └─ Characteristic: 26eb002f-b012-49a8-b1f8-394fb2032b0f (WRITE | NOTIFY)            handle 0x0019  SP_DATA
     */

    override fun isServiceSupported(handle: GetSetMode): Boolean {
        val uuid = when (handle) {
            GetSetMode.GET -> CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID
            GetSetMode.SET -> CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID
            GetSetMode.NOTIFY -> CasioConstants.CASIO_NOTIFICATION_CHARACTERISTIC_UUID
            GetSetMode.SP_REQUEST -> CasioConstants.CASIO_SET_CONFIGURATION_CHARACTERISTIC_UUID
            GetSetMode.SP_DATA -> CasioConstants.CASIO_GET_CONFIGURATION_CHARACTERISTIC_UUID
        }
        return characteristicUUIDs.containsKey(uuid.toString())
    }

    override suspend fun write(handle: GetSetMode, data: ByteArray) {
        if (characteristicUUIDs.isEmpty()) {
            ProgressEvents.onNext("ApiError", "Not connected to watch")
            disconnect()
            return
        }

        if (!isServiceSupported(handle)) {
            Timber.e("${handle.name.lowercase()} feature not supported")
            return
        }

        val characteristic = when (handle) {
            GetSetMode.GET -> readCharacteristicHolder
            GetSetMode.SET -> writeCharacteristicHolder
            GetSetMode.NOTIFY -> writeCharacteristicHolderNotifications
            GetSetMode.SP_REQUEST -> writeCharacteristicHolderSPRequest
            GetSetMode.SP_DATA -> writeCharacteristicHolderSPData
        }

        val writeType =
            if (handle == GetSetMode.GET || handle == GetSetMode.NOTIFY || handle == GetSetMode.SP_REQUEST)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        try {
            writeCharacteristic(characteristic, data, writeType).enqueue()
        } catch (e: Exception) {
            Timber.e(e, "Error writing to characteristic ${characteristic?.uuid}")
            ProgressEvents.onNext("ApiError", "Failed to write data: ${e.message}")
            disconnect()
        }
    }
}