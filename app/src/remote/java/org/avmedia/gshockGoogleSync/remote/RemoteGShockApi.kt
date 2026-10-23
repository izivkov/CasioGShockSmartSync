package org.avmedia.gshockGoogleSync.remote

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockGoogleSync.utils.subscribeToProgressEvents
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ICDPDelegate
import org.avmedia.gshockapi.IGShockAPI
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.model.StepCounterData
import org.json.JSONArray
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
/** Maps the handles the Python server writes to (see gshock_api Connection.handles_map) onto the library's GATT modes. */
private val handleToMode: Map<Int, GetSetMode> = mapOf(
    0x0C to GetSetMode.GET,            // read request for all features
    0x0D to GetSetMode.NOTIFY,         // notification characteristic
    0x0E to GetSetMode.SET,            // all features (set)
    0x11 to GetSetMode.DATA_REQUEST,
    0x17 to GetSetMode.SP_REQUEST,
    0x19 to GetSetMode.SP_DATA,
)

@Singleton
class RemoteGShockApi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleProxy: BleProxy
) : IGShockAPI {

    private val localBleApi = GShockAPI(context)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Timber.i("Initializing RemoteGShockApi (Flavor: Remote)")
        bleProxy.onWriteReceived = { handle, data -> writeToWatch(handle, data) }
        bleProxy.connect()
    }

    /** Performs a GATT write the Python server asked for, using the phone's BLE link. */
    private fun writeToWatch(handle: Int, data: ByteArray) {
        val mode = handleToMode[handle]
        if (mode == null) {
            Timber.w("No GATT mode for handle 0x%02X; dropping write", handle)
            return
        }
        Connection.write(mode, data)
    }

    /**
     * Forwards every watch notification to the server while still passing it to the library's
     * dispatcher, so the library's own handling keeps working.
     */
    private fun installNotificationTap() {
        Connection.setDataCallback(object : IDataReceived {
            override fun dataReceived(data: String?) = forwardNotification(null, data)
            override fun dataReceived(characteristicUuid: String, data: String?) =
                forwardNotification(characteristicUuid, data)
        })
    }

    private fun forwardNotification(characteristicUuid: String?, data: String?) {
        if (data == null) return
        val hex = data.removePrefix("0x").replace(" ", "")
        bleProxy.onNotification(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), characteristicUuid)
        MessageDispatcher.onReceived(data, WatchInfo.protocol)
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

    private var reportedWatchState: Boolean? = null

    override fun isConnected(): Boolean {
        val connected = localBleApi.isConnected()
        reportWatchState(connected)
        return connected
    }

    /** Tells the Python server whether the phone holds a watch link; sent only on change. */
    private fun reportWatchState(connected: Boolean) {
        if (reportedWatchState == connected) return
        reportedWatchState = connected
        if (connected) {
            installNotificationTap()
            bleProxy.onWatchConnected(name = WatchInfo.name)
        } else {
            bleProxy.onWatchDisconnected()
        }
    }

    // Report link changes as the library observes them, instead of waiting for someone to poll
    // isConnected(). Without this the server keeps sending commands to a watch that has dropped.
    private val watchStateSubscription = subscribeToProgressEvents(
        "remote-watch-state",
        arrayOf(
            EventAction("ConnectionSetupComplete") { reportWatchState(true) },
            EventAction("Disconnect") { reportWatchState(false) },
        ),
    )

    override fun teardownConnection(device: BluetoothDevice) {
        localBleApi.teardownConnection(device)
    }

    // Button state describes how the phone's own BLE link was started, so it is answered from the
    // local library. A fresh read through the server returns the watch's idle code (0), which both
    // libraries map to LOWER_LEFT, so it cannot tell a lower-right press apart.
    override suspend fun getPressedButton(): IO.WatchButton = localBleApi.getPressedButton()

    override fun isActionButtonPressed(): Boolean = localBleApi.isActionButtonPressed()
    override fun isAlwaysConnectedConnectionPressed(): Boolean = localBleApi.isAlwaysConnectedConnectionPressed()
    override fun isNormalButtonPressed(): Boolean = localBleApi.isNormalButtonPressed()
    override fun isAutoTimeStarted(): Boolean = localBleApi.isAutoTimeStarted()
    override fun isFindPhoneButtonPressed(): Boolean = localBleApi.isFindPhoneButtonPressed()

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
        return watchCondition()?.optInt("battery_level_percent", 0) ?: 0
    }

    override suspend fun getWatchTemperature(): Int {
        return watchCondition()?.optInt("temperature", 0) ?: 0
    }

    private suspend fun watchCondition(): JSONObject? {
        return bleProxy.sendCommand("get_watch_condition") as? JSONObject
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
        scope.launch {
            bleProxy.sendCommand("set_timer", mapOf("timer_value" to timerValue))
        }
    }

    override suspend fun getAppInfo(): String {
        val res = bleProxy.sendCommand("get_app_info")
        return res?.toString() ?: "GShockRemote Bridge 1.0"
    }

    override suspend fun setTime(timeZone: String, timeMs: Long?, offsetFormSystemTime: Long?) {
        val args = mutableMapOf<String, Any>("timezone" to timeZone)
        if (timeMs != null) args["time_ms"] = timeMs
        if (offsetFormSystemTime != null) args["offset"] = offsetFormSystemTime
        bleProxy.sendCommand("set_time", args)
    }

    override suspend fun getAlarms(): ArrayList<Alarm> {
        val res = bleProxy.sendCommand("get_alarms")
        return parseAlarms(res)
    }

    override fun setAlarms(alarms: ArrayList<Alarm>) {
        scope.launch {
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
        scope.launch {
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
        scope.launch {
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
        scope.launch {
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

    private fun toJsonString(result: Any?): String = when (result) {
        is String -> result
        is JSONObject -> result.toString()
        is JSONArray -> result.toString()
        else -> gson.toJson(result)
    }

    private fun <T> parseJsonResult(result: Any?, clazz: Class<T>): T? {
        return try {
            gson.fromJson(toJsonString(result), clazz)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse result for ${clazz.simpleName}")
            null
        }
    }

    private fun parseAlarms(result: Any?): ArrayList<Alarm> {
        return try {
            val jsonStr = toJsonString(result)
            val type = object : TypeToken<ArrayList<Alarm>>() {}.type
            gson.fromJson(jsonStr, type) ?: arrayListOf()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse alarms")
            arrayListOf()
        }
    }

    private fun parseEvents(result: Any?): ArrayList<Event> {
        return try {
            val jsonStr = toJsonString(result)
            val type = object : TypeToken<ArrayList<Event>>() {}.type
            gson.fromJson(jsonStr, type) ?: arrayListOf()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse events")
            arrayListOf()
        }
    }
}
