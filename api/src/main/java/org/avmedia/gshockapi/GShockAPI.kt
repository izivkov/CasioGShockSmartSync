package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GShockPairingManager
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.io.AlarmsIO
import org.avmedia.gshockapi.io.AppInfoIO
import org.avmedia.gshockapi.io.AppInfoIOFunctional
import org.avmedia.gshockapi.io.AppNotificationIO
import org.avmedia.gshockapi.io.ButtonPressedIO
import org.avmedia.gshockapi.io.DstForWorldCitiesIO
import org.avmedia.gshockapi.io.DstWatchStateIO
import org.avmedia.gshockapi.io.ErrorIO
import org.avmedia.gshockapi.io.EventsIO
import org.avmedia.gshockapi.io.HomeTimeIO
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.IO.writeCmd
import org.avmedia.gshockapi.io.SettingsIO
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import org.avmedia.gshockapi.io.TimeIO
import org.avmedia.gshockapi.io.TimerIO
import org.avmedia.gshockapi.io.WaitForConnectionIO
import org.avmedia.gshockapi.io.WatchConditionIO
import org.avmedia.gshockapi.io.WatchNameIO
import org.avmedia.gshockapi.io.WorldCitiesIO
import timber.log.Timber
import java.time.ZoneId
import java.util.TimeZone

/**
 * This class contains all the API functions. This should the the main interface to the library.
 */

@RequiresApi(Build.VERSION_CODES.O)
class GShockAPI(private val context: Context) : IGShockAPI {

    override suspend fun waitForConnection(deviceId: String?) {
        Connection.init(context)
        val connectedStatus = WaitForConnectionIO.request(context, deviceId)
        if (connectedStatus == "OK") {
            init()
        }
    }

    override suspend fun init(): Boolean {
        IO.init()
        getAppInfo()

        getPressedButton()
        ProgressEvents.onNext("ButtonPressedInfoReceived")
        ProgressEvents.onNext("WatchInitializationCompleted")
        return true
    }

    override fun scan(
        context: Context,
        filter: (DeviceInfo) -> Boolean,
        onDeviceFound: (DeviceInfo) -> Unit
    ) {
        Connection.scan(
            context,
            { isBluetoothEnabled(context) },
            filter,
            onDeviceFound
        )
    }

    override fun stopScan() {
        Connection.stopScan()
    }

    override fun startFallbackScan(
        context: Context,
        addresses: List<String>,
        pendingIntent: android.app.PendingIntent
    ) {
        Connection.startFallbackScan(context, addresses, pendingIntent)
    }

    override fun isConnected(): Boolean =
        Connection.isConnected()

    override fun teardownConnection(device: BluetoothDevice) {
        Connection.teardownConnection()
    }

    override suspend fun getPressedButton(): IO.WatchButton {
        val value = ButtonPressedIO.request()
        ButtonPressedIO.put(value)
        return value
    }

    override fun isActionButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.LOWER_RIGHT
    }

    override fun isAlwaysConnectedConnectionPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.ALLAYS_CONNECTED_CONNECTION
    }

    override fun isNormalButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.LOWER_LEFT
    }

    override fun isAutoTimeStarted(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.NO_BUTTON
    }

    override fun isFindPhoneButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.FIND_PHONE
    }

    override suspend fun getWatchName(): String {
        return WatchNameIO.request()
    }

    override suspend fun getError(): String {
        return ErrorIO.request()
    }

    override suspend fun getDSTWatchState(state: IO.DstState): String {
        return DstWatchStateIO.request(state)
    }

    override suspend fun getDSTForWorldCities(cityNumber: Int): String {
        return DstForWorldCitiesIO.request(cityNumber)
    }

    override suspend fun getWorldCities(cityNumber: Int): String {
        return WorldCitiesIO.request(cityNumber)
    }

    override suspend fun getHomeTime(): String {
        return HomeTimeIO.request()
    }

    override suspend fun getBatteryLevel(): Int {
        return WatchConditionIO.request().batteryLevel
    }

    override suspend fun getWatchTemperature(): Int {
        return WatchConditionIO.request().temperature
    }

    override suspend fun getTimer(): Int {
        return TimerIO.request()
    }

    override fun setTimer(timerValue: Int) {
        TimerIO.set(timerValue)
    }

    override suspend fun getAppInfo(): String {
        return AppInfoIO.request()
    }

    override suspend fun setScratchpadData(data: ByteArray) {
        AppInfoIO.setUserData(data)
    }

    override suspend fun getScratchpadData(
        oldLayout: Map<String, IntArray>?,
        newLayout: Map<String, IntArray>?
    ): ByteArray {
        AppInfoIO.request()
        val currentData = AppInfoIO.getUserData(0, AppInfoIOFunctional.BUFFER_SIZE - AppInfoIOFunctional.USER_DATA_START_INDEX)
        
        if (AppInfoIO.shouldMigrate() && oldLayout != null && newLayout != null) {
            return AppInfoIOFunctional.migrate(currentData, oldLayout, newLayout)
        }
        return currentData
    }

    override fun isScratchpadReset(): Boolean {
        return AppInfoIO.wasScratchpadReset
    }

    override suspend fun setTime(timeZone: String, timeMs: Long?) {
        if (!ZoneId.getAvailableZoneIds().contains(timeZone)) {
            Timber.e("setTime: Invalid timezone $timeZone passed")
            ProgressEvents.onNext("ApiError")
            return
        }
        TimeIO.setTimezone(timeZone)
        TimeIO.set(timeMs)
    }

    override suspend fun getAlarms(): ArrayList<Alarm> {
        return AlarmsIO.request()
    }

    override fun setAlarms(alarms: ArrayList<Alarm>) {
        AlarmsIO.set(alarms)
    }

    override suspend fun getEventsFromWatch(): ArrayList<Event> {
        val events = ArrayList<Event>()
        events.add(getEventFromWatch(1))
        events.add(getEventFromWatch(2))
        events.add(getEventFromWatch(3))
        events.add(getEventFromWatch(4))
        events.add(getEventFromWatch(5))
        return events
    }

    override suspend fun getEventFromWatch(eventNumber: Int): Event {
        return EventsIO.request(eventNumber)
    }

    override fun setEvents(events: ArrayList<Event>) {
        EventsIO.set(events)
    }

    override fun clearEvents() {
        EventsIO.clearAll()
    }

    override suspend fun getSettings(): Settings {
        val settings = getBasicSettings()
        val timeAdjustment = getTimeAdjustment()
        settings.timeAdjustment = timeAdjustment.isTimeAdjustmentSet
        settings.adjustmentTimeMinutes = timeAdjustment.adjustmentTimeMinutes
        return settings
    }

    override suspend fun getBasicSettings(): Settings {
        return SettingsIO.request()
    }

    override suspend fun getTimeAdjustment(): TimeAdjustmentInfo {
        return TimeAdjustmentIO.request()
    }

    override fun sendAppNotification(notification: AppNotification) {
        val encodedBuffer = AppNotificationIO.encodeNotificationPacket(notification)
        val encryptedBuffer = AppNotificationIO.xorEncodeBuffer(encodedBuffer)
        writeCmd(GetSetMode.NOTIFY, encryptedBuffer)
    }

    override fun supportsAppNotifications(): Boolean =
        Connection.isServiceSupported(GetSetMode.NOTIFY)

    override fun setSettings(settings: Settings) {
        SettingsIO.set(settings)
        TimeAdjustmentIO.set(settings)
    }

    override fun disconnect() {
        Connection.disconnect()
    }

    override fun close() {
        Connection.close()
    }

    override fun isBluetoothEnabled(context: Context): Boolean {
        return Connection.isBluetoothEnabled(context)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun sendMessage(message: String) {
        MessageDispatcher.sendToWatch(message)
    }

    override fun resetHand() {
        sendMessage("{action: \"RESET_HAND\", value: \"\"}")
    }

    override fun validateBluetoothAddress(deviceAddress: String?): Boolean {
        return Connection.validateAddress(deviceAddress)
    }

    override fun preventReconnection(): Boolean {
        return true
    }

    override fun associate(context: Context, delegate: ICDPDelegate) {
        GShockPairingManager.associate(context, delegate::onChooserReady, delegate::onError)
    }

    override fun disassociate(context: Context, address: String) {
        GShockPairingManager.disassociate(context, address)
    }

    override fun getAssociations(context: Context): List<String> {
        return GShockPairingManager.getAssociations(context)
    }

    override fun getAssociationsWithNames(context: Context): List<IGShockAPI.Association> {
        return GShockPairingManager.getAssociationsWithNames(context)
    }

    @RequiresPermission("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun startObservingDevicePresence(context: Context, address: String) {
        GShockPairingManager.startObservingDevicePresence(context, address)
    }

    @RequiresPermission("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun stopObservingDevicePresence(context: Context, address: String) {
        GShockPairingManager.stopObservingDevicePresence(context, address)
    }
}
