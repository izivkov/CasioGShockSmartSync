package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import java.util.TimeZone

/**
 * Main interface for communicating with Casio G-Shock watches over Bluetooth.
 */
interface IGShockAPI {

    suspend fun waitForConnection(deviceId: String? = "")
    suspend fun init(): Boolean
    fun isConnected(): Boolean
    fun teardownConnection(device: BluetoothDevice)
    suspend fun getPressedButton(): IO.WatchButton
    fun isActionButtonPressed(): Boolean
    fun isAlwaysConnectedConnectionPressed(): Boolean
    fun isNormalButtonPressed(): Boolean
    fun isAutoTimeStarted(): Boolean
    fun isFindPhoneButtonPressed(): Boolean
    suspend fun getWatchName(): String
    suspend fun getError(): String
    suspend fun getDSTWatchState(state: IO.DstState): String
    suspend fun getDSTForWorldCities(cityNumber: Int): String
    suspend fun getWorldCities(cityNumber: Int): String
    suspend fun getHomeTime(): String
    suspend fun getBatteryLevel(): Int
    suspend fun getWatchTemperature(): Int
    suspend fun getTimer(): Int
    fun setTimer(timerValue: Int)
    suspend fun getAppInfo(): String
    suspend fun setTime(timeZone: String = TimeZone.getDefault().id, timeMs: Long? = null)
    suspend fun getAlarms(): ArrayList<Alarm>
    fun setAlarms(alarms: ArrayList<Alarm>)
    suspend fun getEventsFromWatch(): ArrayList<Event>
    suspend fun getEventFromWatch(eventNumber: Int): Event
    fun setEvents(events: ArrayList<Event>)
    fun clearEvents()
    suspend fun getSettings(): Settings
    suspend fun getBasicSettings(): Settings
    suspend fun getTimeAdjustment(): TimeAdjustmentInfo
    fun setSettings(settings: Settings)
    fun disconnect()
    fun close()
    fun isBluetoothEnabled(context: Context): Boolean
    fun sendAppNotification(notification: AppNotification)
    fun supportsAppNotifications(): Boolean

    /**
     * Writes data to the watch's scratchpad.
     * @param data The user data part of the scratchpad.
     */
    suspend fun setScratchpadData(data: ByteArray)

    /**
     * Reads the user data area from the watch's scratchpad.
     * Returns ONLY the user data part.
     * @param oldLayout Optional source layout for migration if the watch currently holds legacy data.
     * @param newLayout Optional target layout for migration.
     */
    suspend fun getScratchpadData(
        oldLayout: Map<String, IntArray>? = null, 
        newLayout: Map<String, IntArray>? = null
    ): ByteArray

    fun isScratchpadReset(): Boolean

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendMessage(message: String)
    fun resetHand()
    fun validateBluetoothAddress(deviceAddress: String?): Boolean
    fun preventReconnection(): Boolean

    fun associate(context: Context, delegate: ICDPDelegate)
    fun disassociate(context: Context, address: String)
    data class Association(val address: String, val name: String?)
    fun getAssociationsWithNames(context: Context): List<Association>
    fun getAssociations(context: Context): List<String>

    @RequiresPermission("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE")
    @RequiresApi(Build.VERSION_CODES.S)
    fun startObservingDevicePresence(context: Context, address: String)

    @RequiresPermission("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE")
    @RequiresApi(Build.VERSION_CODES.S)
    fun stopObservingDevicePresence(context: Context, address: String)

    fun scan(context: Context, filter: (DeviceInfo) -> Boolean, onDeviceFound: (DeviceInfo) -> Unit)
    fun stopScan()
    fun startFallbackScan(context: Context, addresses: List<String>, pendingIntent: android.app.PendingIntent)
}
