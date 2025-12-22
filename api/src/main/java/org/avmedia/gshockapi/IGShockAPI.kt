package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import org.avmedia.gshockapi.ICDPDelegate
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.ble.GShockPairingManager
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import java.util.TimeZone

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

    suspend fun setScratchpadData(data: ByteArray, startIndex: Int)

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendMessage(message: String)
    fun resetHand()
    fun validateBluetoothAddress(deviceAddress: String?): Boolean
    fun preventReconnection(): Boolean
    suspend fun getScratchpadData(index: Int, length: Int): ByteArray
    fun isScratchpadReset(): Boolean

    fun associate(context: Context, delegate: ICDPDelegate)
    fun disassociate(context: Context, address: String)

    data class Association(val address: String, val name: String?)
    fun getAssociationsWithNames(context: Context): List<Association>

    fun getAssociations(context: Context): List<String>

    fun scan(context: Context, filter: (DeviceInfo) -> Boolean, onDeviceFound: (DeviceInfo) -> Unit)
}
