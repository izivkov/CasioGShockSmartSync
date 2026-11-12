package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository

/**
 * The part of the watch connection that the [Session] layer needs: which button started the
 * connection, and a way to release it. A narrow seam so the session logic can run in plain JVM
 * tests with a fake instead of the whole BLE API.
 */
interface ConnectionGateway {
    fun isActionButtonPressed(): Boolean
    fun isAutoTimeStarted(): Boolean
    fun isFindPhoneButtonPressed(): Boolean
    fun isAlwaysConnectedConnectionPressed(): Boolean
    fun teardownConnection(device: BluetoothDevice)
    fun disconnect()
}

/** The production [ConnectionGateway], backed by the app's [GShockRepository]. */
class RepositoryConnectionGateway(
    private val repository: GShockRepository
) : ConnectionGateway {
    override fun isActionButtonPressed() = repository.isActionButtonPressed()
    override fun isAutoTimeStarted() = repository.isAutoTimeStarted()
    override fun isFindPhoneButtonPressed() = repository.isFindPhoneButtonPressed()
    override fun isAlwaysConnectedConnectionPressed() = repository.isAlwaysConnectedConnectionPressed()
    override fun teardownConnection(device: BluetoothDevice) = repository.teardownConnection(device)
    override fun disconnect() = repository.disconnect()
}
