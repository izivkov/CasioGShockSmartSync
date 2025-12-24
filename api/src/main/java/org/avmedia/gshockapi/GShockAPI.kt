package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GShockPairingManager
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.io.AlarmsIO
import org.avmedia.gshockapi.io.AppInfoIO
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

/**
 * This class contains all the API functions. This should the the main interface to the library.
 *
 *
 * Here is how to use it:
 *
 * ```
 * private val api = GShockAPI(this)
 *
 * class MainActivity : AppCompatActivity() {
 *  override fun onCreate(savedInstanceState: Bundle?) {
 *      super.onCreate(savedInstanceState)
 *      ...
 *      GlobalScope.launch {
 *          api.waitForConnection(this)
 *          api.getPressedButton()
 *          api.getWatchName()
 *          api.getBatteryLevel()
 *          api.getTimer()
 *          api.getAppInfo()
 *          api.getHomeTime()
 *          api.setTime()
 *          api.sendAppNotification()
 *          ...
 *      }
 *  }
 * }
 * ```
 */

@RequiresApi(Build.VERSION_CODES.O)
class GShockAPI(private val context: Context) : IGShockAPI {

    /**
     * This function waits for the watch to connect to the phone.
     * When connected, it returns and emits a `ConnectionSetupComplete` event, which
     * can inform other parts of the app that the connection has taken place.
     * @param[deviceId] Optional parameter containing a the Bluetooth ID
     * of a watch which was previously connected. Providing this parameter will
     * speed up connection. Its value can be saved in local storage for future use,
     * and can be obtained after connection by calling `getDeviceId()`. Here is an example:
     * ```
     * private suspend fun waitForConnectionCached() {
     *      var cachedDeviceAddress: String? =
     *      LocalDataStorage.get("cached device", null, this@MainActivity)
     *      api().waitForConnection(cachedDeviceAddress)
     *      LocalDataStorage.put("cached device", api().getDeviceId(), this@MainActivity)
     *   }
     * ```
     */

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
        Connection.scan(context, filter, onDeviceFound)
    }

    /**
     * Returns a Boolean value indicating if the watch is currently commenced to the phone
     */
    override fun isConnected(): Boolean =
        Connection.isConnected()

    /**
     * Close the connection and free all associated resources.
     * @param[deviceId] The deviceId associated with current connection.
     * The `deviceId` can be obtained by calling `getDeviceId()` or from the
     * payload in the `ProgressEvents.Events.Disconnect` event
     */
    override fun teardownConnection(device: BluetoothDevice) {
        Connection.teardownConnection()
    }

    /**
     * This function tells us which button was pressed on the watch to
     * initiate the connection. Remember, the connection between the phone and the
     * watch can only be initiated from the <b>watch</b>.
     *
     *
     * The return values are interpreted as follows:
     *
     * - `LOWER_LEFT` - this connection is initiated by a long-press of the lower-left button on the watch.
     * The app receiving this type of connection can now send and receive commands to the watch.
     * - `LOWER_RIGHT` - this connection is initiated by a short-press of the lower-right button,
     * which is usually used to set time. But the app can use this signal to perform other arbitrary functions.
     * Therefore, this button is also referred as `ACTION BUTTON`.
     * The connection will automatically disconnect in about 20 seconds.
     * - `NO_BUTTON` - this connection is initiated automatically, periodically
     * from the watch, without user input. It will automatically disconnect in about 20 seconds.
     *
     *
     * *This function is relatively expensive, since it performs round trip to the watch to get the value. Therefore, it should be called only once each time the connection is established. The returned values will not change for the duration of the connection. After that, the user can call one of these lightweight functions:*
     *
     *
     *   [isActionButtonPressed]
     *
     *
     *   [isNormalButtonPressed]
     *
     *
     *   [isAutoTimeStarted]
     *
     *
     * @return [BluetoothWatch.WATCH_BUTTON]
     **
     * @seeCasioIO.WATCH_BUTTON
     */
    /* Do not get value from cache, because we do not want to
    get all values here. */
    override suspend fun getPressedButton(): IO.WatchButton {
        val value = ButtonPressedIO.request()
        ButtonPressedIO.put(value)
        return value
    }

    /**
     * This function tells us if the connection was initiated by short-pressing the lower-right button on the
     * watch, also known as ACTION BUTTON
     *
     * @return **true** if the lower-right button initiated the connection, **false** otherwise.
     */
    override fun isActionButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.LOWER_RIGHT
    }

    override fun isAlwaysConnectedConnectionPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.ALLAYS_CONNECTED_CONNECTION
    }

    /**
     * This function tells us if the connection was initiated by long-pressing the lower-left
     * button on the watch
     *
     * @return **true** if the lower-left button initiated the connection, **false** otherwise.
     */
    override fun isNormalButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.LOWER_LEFT
    }

    /**
     * This function tells us if the connection was initiated automatically by the watch, without the user
     * pressing any button. This happens if Auto-Time is set in the setting. In this case, the
     * watch will periodically connect at around 00:30, 06:30, 12:30 and 18:30
     *
     * @return **true** if watch automatically initiated the connection, **false** otherwise.
     */
    override fun isAutoTimeStarted(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.NO_BUTTON
    }

    /**
     * This function tells us if the connection was initiated by ling-pressing the lower-right button on the
     * watch, used to activate FIND PHONE action
     *
     * @return **true** if button pressed to activate FIND PHONE function, **false** otherwise.
     */
    override fun isFindPhoneButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == IO.WatchButton.FIND_PHONE
    }

    /**
     * Get the name of the watch.
     *
     * @return returns the name of the watch as a String. i.e. "GW-B5600"
     */
    override suspend fun getWatchName(): String {
        return WatchNameIO.request()
    }

    override suspend fun getError(): String {
        return ErrorIO.request()
    }

    /**
     * Get the DST state of the watch.
     *
     * @return returns the Daylight Saving Time state of the watch as a String.
     */
    override suspend fun getDSTWatchState(state: IO.DstState): String {
        return DstWatchStateIO.request(state)
    }

    /**
     * Get the **Daylight Saving Time** for a particular World City set on the watch.
     * There are 6 world cities that can be stored.
     *
     * @param cityNumber: index of the world city (0..5)
     *
     * @return Daylight Saving Time state of the requested World City as a String.
     */
    override suspend fun getDSTForWorldCities(cityNumber: Int): String {
        return DstForWorldCitiesIO.request(cityNumber)
    }

    /**
     * Get the name for a particular World City set on the watch.
     * There are 6 world cities that can be stored.
     *
     * @param cityNumber Index of the world city (0..5)
     *
     * @return The name of the requested World City as a String.
     */
    override suspend fun getWorldCities(cityNumber: Int): String {
        return WorldCitiesIO.request(cityNumber)
    }

    /**
     * Get Home Time, (Home City).
     *
     * @return The name of Home City as a String.
     */
    override suspend fun getHomeTime(): String {
        return HomeTimeIO.request()
    }

    /**
     * Get Battery level.
     *
     * @return the battery level in percent as a String. E.g.: 83
     */
    override suspend fun getBatteryLevel(): Int {
        return WatchConditionIO.request().batteryLevel
    }

    /**
     * Get Watch Temperature.
     *
     * @return the watch's temperature in degree Celsius
     */
    override suspend fun getWatchTemperature(): Int {
        return WatchConditionIO.request().temperature
    }

    /**
     * Get Timer value in seconds.
     *
     * @return The timer number in seconds as an Int. E.g.: 180 means the timer is set for 3 minutes.
     */
    override suspend fun getTimer(): Int {
        return TimerIO.request()
    }

    /**
     * Set Timer value in seconds.
     *
     * @param timerValue Timer number of seconds as an Int.  E.g.: 180 means the timer will be set for 3 minutes.
     */
    override fun setTimer(timerValue: Int) {
        TimerIO.set(timerValue)
    }

    /**
     * Gets and internally sets app info to the watch.
     * This is needed to re-enable lower-right button after the watch has been reset or BLE has been cleared.
     * Call this function after each time the connection has been established.
     *
     * @return appInfo string from the watch.
     */
    override suspend fun getAppInfo(): String {
        return AppInfoIO.request()
    }

    override suspend fun setScratchpadData(data: ByteArray, startIndex: Int) {
        AppInfoIO.setUserData(data, startIndex)
    }

    override suspend fun getScratchpadData(index: Int, length: Int): ByteArray {
        AppInfoIO.request()
        return AppInfoIO.getUserData(index, length)
    }

    override fun isScratchpadReset(): Boolean {
        return AppInfoIO.wasScratchpadReset
    }


    /**
     * Sets the current time on the watch from the time on the phone. In addition, it can optionally set the Home Time
     * to the current time zone. If timezone changes during travel, the watch will automatically be set to the
     * correct time and timezone after running this function.
     *
     * @param timeZone Optional String parameter of form "region/city', i.e.: "Europe/Sofia".
     * Example:
     * ```
     *      setTime()
     *      setTime(TimeZone.getDefault().id)
     *      setTime("Europe/Sofia")
     *  ```
     */
    override suspend fun setTime(timeZone: String, timeMs: Long?) {

        if (!ZoneId.getAvailableZoneIds().contains(timeZone)) {
            Timber.e("setTime: Invalid timezone $timeZone passed")
            ProgressEvents.onNext("ApiError")
            return
        }

        TimeIO.setTimezone(timeZone)
        TimeIO.set(timeMs)
    }

    /**
     * Gets the current alarms from the watch. Up to 5 alarms are supported on the watch.
     *
     * @return ArrayList<[Alarm]>
     */

    override suspend fun getAlarms(): ArrayList<Alarm> {
        return AlarmsIO.request()
    }

    /**
     * Sets alarms to the watch. Up to 5 alarms are supported on the watch.
     *
     * @param ArrayList<[Alarm]>
     */

    override fun setAlarms(alarms: ArrayList<Alarm>) {
        AlarmsIO.set(alarms)  // Renamed for clarity
    }

    /**
     * Gets the current events (reminders) from the watch. Up to 5 events are supported.
     *
     * @return ArrayList<[Event]>
     */
    override suspend fun getEventsFromWatch(): ArrayList<Event> {

        val events = ArrayList<Event>()

        events.add(getEventFromWatch(1))
        events.add(getEventFromWatch(2))
        events.add(getEventFromWatch(3))
        events.add(getEventFromWatch(4))
        events.add(getEventFromWatch(5))

        return events
    }

    /**
     * Gets a single event (reminder) from the watch.
     *
     * @param eventNumber The index of the event 1..5
     * @return [Event]
     */
    override suspend fun getEventFromWatch(eventNumber: Int): Event {
        return EventsIO.request(eventNumber)
    }

    /**
     * Sets events (reminders) to the watch. Up to 5 events are supported.
     *
     * @param ArrayList<[Event]>
     */
    override fun setEvents(events: ArrayList<Event>) {
        EventsIO.set(events)
    }

    /**
     * Clears all  events (reminders) from the watch. Up to 5 events are supported.
     *
     * @param none
     */
    override fun clearEvents() {
        EventsIO.clearAll()
    }

    /**
     * Get settings from the watch. Example:
     *
     * ```
     *      val settings: Settings = getSettings()
     *      settings.dateFormat = "MM:DD"
     *      ...
     *      setSettings(settings)
     * ```
     * @return [Settings]
     */

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

    /**
     * Sends a notification to the watch display.
     *
     * The notification can be one of the following types:
     * - CALENDAR: Calendar events and reminders
     * - EMAIL: Email notifications
     * - EMAIL_SMS: Email or SMS messages that can include Unicode characters
     *
     * The function performs these steps:
     * 1. Encodes the notification into a byte buffer
     * 2. Encrypts the buffer using XOR encoding
     * 3. Sends the encrypted data to the watch
     *
     * Example usage:
     * ```kotlin
     * val notification = AppNotification(
     *     type = NotificationType.CALENDAR,
     *     timestamp = "20240320T143000",
     *     app = "Calendar",
     *     title = "Team Meeting",
     *     text = "2:30 PM - 3:30 PM"
     * )
     * sendAppNotification(notification)
     *
     * @param notification AppNotification object containing all notification details
     * @see AppNotification for notification object structure
     * @see NotificationType for supported notification types */
    override fun sendAppNotification(notification: AppNotification) {
        val encodedBuffer = AppNotificationIO.encodeNotificationPacket(notification)
        val encryptedBuffer = AppNotificationIO.xorEncodeBuffer(encodedBuffer)
        writeCmd(GetSetMode.NOTIFY, encryptedBuffer)
    }

    override fun supportsAppNotifications(): Boolean =
        Connection.isServiceSupported(GetSetMode.NOTIFY)

    /**
     * Set settings to the watch. Populate a [Settings] and call this function. Example:
     *
     * ```
     *      val settings: Settings = getSettings()
     *      settingsSimpleModel.dateFormat = "MM:DD"
     *      ...
     *      setSettings(settings)
     * ```
     *
     * @param settings
     */
    override fun setSettings(settings: Settings) {
        SettingsIO.set(settings)
        TimeAdjustmentIO.set(settings)
    }

    /**
     * Get the Bluetooth ID of the connected watch
     *
     * @return watch's Bluetooth ID as a String. Should look something like: "ED:85:83:38:62:17"
     */

    /**
     * Disconnect from the watch
     *
     * @param context [Context]
     */
    override fun disconnect() {
        Connection.disconnect()
    }

    override fun close() {
        Connection.close()
    }

    /**
     * Tells us if Bluetooth is currently enabled on the phone. If not, the app can take action to enable it.
     *
     * @return *true* if enables, *false* otherwise.
     */
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
}
