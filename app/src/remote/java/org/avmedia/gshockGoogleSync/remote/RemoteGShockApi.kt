package org.avmedia.gshockGoogleSync.remote

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ICDPDelegate
import org.avmedia.gshockapi.IGShockAPI
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.model.StepCounterData
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RemoteGShockApi — IGShockAPI implementation for the remote build flavor.
 *
 * Delegates BLE, scanning, CDM association, connection management, and scratchpad read/write to the standard GShockAPI,
 * while proxying watch command and protocol interactions to the Python server via BleProxy over WebSocket.
 */
@Singleton
class RemoteGShockApi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleProxy: BleProxy
) : IGShockAPI {

    private val localBleApi = GShockAPI(context)
    private val gson = Gson()

    init {
        Timber.i("Initializing RemoteGShockApi (Flavor: Remote)")
        bleProxy.connect()
    }

    override suspend fun waitForConnection(deviceId: String?): Unit {
        while (true) {
            try {
                if (isConnected()) {
                    return
                }
                localBleApi.waitForConnection(deviceId)
                if (isConnected()) {
                    return
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "waitForConnection attempt failed; retrying indefinitely...")
            }
            kotlinx.coroutines.delay(2000L)
        }
    }

    override suspend fun init(): Boolean {
        bleProxy.connect()
        return localBleApi.init()
    }

    override fun isConnected(): Boolean {
        return localBleApi.isConnected()
    }

    override fun teardownConnection(device: BluetoothDevice) {
        localBleApi.teardownConnection(device)
    }

    override suspend fun getPressedButton(): IO.WatchButton {
        val res = bleProxy.sendCommand("get_pressed_button")
        val buttonName = res?.toString() ?: "NO_BUTTON"
        return try {
            IO.WatchButton.valueOf(buttonName)
        } catch (_: Exception) {
            IO.WatchButton.NO_BUTTON
        }
    }

    override fun isActionButtonPressed(): Boolean = false
    override fun isAlwaysConnectedConnectionPressed(): Boolean = false
    override fun isNormalButtonPressed(): Boolean = false
    override fun isAutoTimeStarted(): Boolean = false
    override fun isFindPhoneButtonPressed(): Boolean = false

    override suspend fun getWatchName(): String {
        val res = bleProxy.sendCommand("get_watch_name")
        return res?.toString()?.takeIf { it.isNotBlank() } ?: WatchInfo.name.ifEmpty { "G-SHOCK (Remote)" }
    }

    override suspend fun getError(): String = ""

    override suspend fun getDSTWatchState(state: IO.DstState): String {
        val res = bleProxy.sendCommand("get_dst_watch_state", mapOf("state" to state.name))
        return res?.toString() ?: ""
    }

    override suspend fun getDSTForWorldCities(cityNumber: Int): String {
        val res = bleProxy.sendCommand("get_dst_for_world_cities", mapOf("city_number" to cityNumber))
        return res?.toString() ?: ""
    }

    override suspend fun getWorldCities(cityNumber: Int): String {
        val res = bleProxy.sendCommand("get_world_cities", mapOf("city_number" to cityNumber))
        return res?.toString() ?: ""
    }

    override suspend fun getHomeTime(): String {
        val res = bleProxy.sendCommand("get_home_time")
        return res?.toString() ?: "UTC"
    }

    override suspend fun getBatteryLevel(): Int {
        bleProxy.sendCommand("get_watch_condition")
        return 100
    }

    override suspend fun getWatchTemperature(): Int {
        bleProxy.sendCommand("get_watch_condition")
        return 20
    }

    override suspend fun getStepCount(peek: Boolean): StepCounterData {
        val res = bleProxy.sendCommand("get_step_count", mapOf("peek" to peek))
        return parseJsonResult(res, StepCounterData::class.java) ?: StepCounterData.unavailable()
    }

    override suspend fun getStepSummary(): Int = 0

    override suspend fun getTimer(): Int {
        val res = bleProxy.sendCommand("get_timer")
        return (res as? Number)?.toInt() ?: 0
    }

    override fun setTimer(timerValue: Int) {
        runBlocking {
            bleProxy.sendCommand("set_timer", mapOf("timer" to timerValue))
        }
    }

    override suspend fun getAppInfo(): String {
        val res = bleProxy.sendCommand("get_app_info")
        return res?.toString() ?: "GShockRemote Bridge 1.0"
    }

    override suspend fun setTime(timeZone: String, timeMs: Long?, offsetFormSystemTime: Long?) {
        val args = mutableMapOf<String, Any>("time_zone" to timeZone)
        if (timeMs != null) args["time_ms"] = timeMs
        if (offsetFormSystemTime != null) args["offset"] = offsetFormSystemTime
        bleProxy.sendCommand("set_time", args)
    }

    override suspend fun getAlarms(): ArrayList<Alarm> {
        val res = bleProxy.sendCommand("get_alarms")
        return parseAlarms(res)
    }

    override fun setAlarms(alarms: ArrayList<Alarm>) {
        runBlocking {
            bleProxy.sendCommand("set_alarms", mapOf("alarms" to alarms))
        }
    }

    override suspend fun getEventsFromWatch(): ArrayList<Event> {
        val res = bleProxy.sendCommand("get_reminders")
        return parseEvents(res)
    }

    override suspend fun getEventFromWatch(eventNumber: Int): Event {
        val res = bleProxy.sendCommand("get_event_from_watch", mapOf("event_number" to eventNumber))
        return parseJsonResult(res, Event::class.java) ?: Event(
            title = "",
            startDate = org.avmedia.gshockapi.model.EventDate(2026, java.time.Month.JANUARY, 1),
            endDate = null,
            repeatPeriod = org.avmedia.gshockapi.model.RepeatPeriod.NEVER,
            daysOfWeek = null,
            enabled = false,
            incompatible = false
        )
    }

    override fun setEvents(events: ArrayList<Event>) {
        runBlocking {
            bleProxy.sendCommand("set_reminders", mapOf("reminders" to events))
        }
    }

    override fun clearEvents() {
        setEvents(arrayListOf())
    }

    override suspend fun getSettings(): Settings {
        val res = bleProxy.sendCommand("get_settings")
        return parseJsonResult(res, Settings::class.java) ?: Settings()
    }

    override suspend fun getBasicSettings(): Settings {
        val res = bleProxy.sendCommand("get_basic_settings")
        return parseJsonResult(res, Settings::class.java) ?: Settings()
    }

    override suspend fun getTimeAdjustment(): TimeAdjustmentInfo {
        val res = bleProxy.sendCommand("get_time_adjustment")
        return parseJsonResult(res, TimeAdjustmentInfo::class.java) ?: TimeAdjustmentInfo(false, 0)
    }

    override fun setSettings(settings: Settings) {
        runBlocking {
            bleProxy.sendCommand("set_settings", mapOf("settings" to settings))
        }
    }

    override fun disconnect() {
        localBleApi.disconnect()
    }

    override fun close() {
        localBleApi.close()
    }

    override fun isBluetoothEnabled(context: Context): Boolean {
        return localBleApi.isBluetoothEnabled(context)
    }

    override fun sendAppNotification(notification: AppNotification) {
        runBlocking {
            bleProxy.sendCommand("send_app_notification", mapOf("notification" to notification))
        }
    }

    override fun supportsAppNotifications(): Boolean {
        return localBleApi.supportsAppNotifications()
    }

    override suspend fun setScratchpadData(data: ByteArray) {
        localBleApi.setScratchpadData(data)
    }

    override fun sendMessage(message: String) {}
    override fun resetHand() {}
    override fun validateBluetoothAddress(deviceAddress: String?): Boolean {
        return localBleApi.validateBluetoothAddress(deviceAddress)
    }

    override fun preventReconnection(): Boolean {
        return localBleApi.preventReconnection()
    }

    override suspend fun getScratchpadData(): ByteArray {
        return localBleApi.getScratchpadData()
    }

    override fun isScratchpadReset(): Boolean {
        return localBleApi.isScratchpadReset()
    }

    override fun associate(context: Context, delegate: ICDPDelegate) {
        localBleApi.associate(context, delegate)
    }

    override fun disassociate(context: Context, address: String) {
        localBleApi.disassociate(context, address)
    }

    override fun getAssociationsWithNames(context: Context): List<IGShockAPI.Association> {
        return localBleApi.getAssociationsWithNames(context)
    }

    override fun getAssociations(context: Context): List<String> {
        return localBleApi.getAssociations(context)
    }

    override fun startObservingDevicePresence(context: Context, address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            localBleApi.startObservingDevicePresence(context, address)
        }
    }

    override fun stopObservingDevicePresence(context: Context, address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            localBleApi.stopObservingDevicePresence(context, address)
        }
    }

    override fun scan(context: Context, filter: (DeviceInfo) -> Boolean, onDeviceFound: (DeviceInfo) -> Unit) {
        localBleApi.scan(context, filter, onDeviceFound)
    }

    override fun stopScan() {
        localBleApi.stopScan()
    }

    override fun startFallbackScan(context: Context, addresses: List<String>, pendingIntent: PendingIntent) {
        localBleApi.startFallbackScan(context, addresses, pendingIntent)
    }

    override fun isConnectedTo(address: String): Boolean {
        return localBleApi.isConnectedTo(address)
    }

    private fun <T> parseJsonResult(result: Any?, clazz: Class<T>): T? {
        return try {
            when (result) {
                is String -> gson.fromJson(result, clazz)
                is JSONObject -> gson.fromJson(result.toString(), clazz)
                else -> {
                    val jsonStr = gson.toJson(result)
                    gson.fromJson(jsonStr, clazz)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse result for ${clazz.simpleName}")
            null
        }
    }

    private fun parseAlarms(result: Any?): ArrayList<Alarm> {
        return try {
            val jsonStr = when (result) {
                is String -> result
                else -> gson.toJson(result)
            }
            val type = object : TypeToken<ArrayList<Alarm>>() {}.type
            gson.fromJson(jsonStr, type) ?: arrayListOf()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse alarms")
            arrayListOf()
        }
    }

    private fun parseEvents(result: Any?): ArrayList<Event> {
        return try {
            val jsonStr = when (result) {
                is String -> result
                else -> gson.toJson(result)
            }
            val type = object : TypeToken<ArrayList<Event>>() {}.type
            gson.fromJson(jsonStr, type) ?: arrayListOf()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse events")
            arrayListOf()
        }
    }
}
