package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.io.*
import org.avmedia.gshockapi.ble.BleScannerLocal
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.Connection.sendMessage
import org.avmedia.gshockapi.casio.*
import org.avmedia.gshockapi.utils.*
import java.util.*

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
 *          ...
 *      }
 *  }
 * }
 * ```
 */
class GShockAPI(private val context: Context) {

    private var bleScannerLocal: BleScannerLocal = BleScannerLocal(context)
    private val resultQueue = ResultQueue<CompletableDeferred<Any>>()

    /**
     * This function waits for the watch to connect to the phone.
     * When connected, it returns and emmits a `ConnectionSetupComplete` event, which
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

    suspend fun waitForConnection(deviceId: String? = "", deviceName: String? = "") {
        bleScannerLocal.stopBleScan()

        val connectedStatus =
            WaitForConnectionIO.request(context, bleScannerLocal, deviceId, deviceName)
        if (connectedStatus == "OK") {
            init()
        }
    }

    private suspend fun init(): Boolean {
        CasioIO.init()
        CachedIO.init()
        getPressedButton()

        ProgressEvents.onNext("ButtonPressedInfoReceived")
        getAppInfo() // this call re-enables lower-right button after watch reset.
        ProgressEvents.onNext("WatchInitializationCompleted")
        return true
    }

    /**
     * Returns a Boolean value indicating if the watch is currently commenced to the phone
     */
    fun isConnected(): Boolean {
        return Connection.isConnected()
    }

    /**
     * Close the connection and free all associated resources.
     * @param[deviceId] The deviceId associated with current connection.
     * The `deviceId` can be obtained by calling `getDeviceId()` or from the
     * payload in the `ProgressEvents.Events.Disconnect` event
     */
    fun teardownConnection(device: BluetoothDevice) {
        Connection.teardownConnection(device)
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
    suspend fun getPressedButton(): CasioIO.WATCH_BUTTON {
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
    fun isActionButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == CasioIO.WATCH_BUTTON.LOWER_RIGHT
    }

    /**
     * This function tells us if the connection was initiated by long-pressing the lower-left
     * button on the watch
     *
     * @return **true** if the lower-left button initiated the connection, **false** otherwise.
     */
    fun isNormalButtonPressed(): Boolean {
        val button = ButtonPressedIO.get()
        return button == CasioIO.WATCH_BUTTON.LOWER_LEFT
    }

    /**
     * This function tells us if the connection was initiated automatically by the watch, without the user
     * pressing any button. This happens if Auto-Time is set in the setting. In this case, the
     * watch will periodically connect at around 00:30, 06:30, 12:30 and 18:30
     *
     * @return **true** if watch automatically initiated the connection, **false** otherwise.
     */
    fun isAutoTimeStarted(): Boolean {
        val button = ButtonPressedIO.get()
        return button == CasioIO.WATCH_BUTTON.NO_BUTTON
    }

    /**
     * Get the name of the watch.
     *
     * @return returns the name of the watch as a String. i.e. "GW-B5600"
     */
    suspend fun getWatchName(): String {
        return WatchNameIO.request()
    }

    suspend fun getError(): String {
        return ErrorIO.request()
    }

    /**
     * Get the DST state of the watch.
     *
     * @return returns the Daylight Saving Time state of the watch as a String.
     */
    suspend fun getDSTWatchState(state: CasioIO.DTS_STATE): String {
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
    suspend fun getDSTForWorldCities(cityNumber: Int): String {
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
    suspend fun getWorldCities(cityNumber: Int): String {
        return WorldCitiesIO.request(cityNumber)
    }

    /**
     * Get Home Time, (Home City).
     *
     * @return The name of Home City as a String.
     */
    suspend fun getHomeTime(): String {
        return HomeTimeIO.request()
    }

    /**
     * Get Battery level.
     *
     * @return the battery level in percent as a String. E.g.: 83
     */
    suspend fun getBatteryLevel(): Int {
        return WatchConditionIO.request().batteryLevel
    }

    /**
     * Get Watch Temperature.
     *
     * @return the watch's temperature in degree Celsius
     */
    suspend fun getWatchTemperature(): Int {
        return WatchConditionIO.request().temperature
    }

    /**
     * Get Timer value in seconds.
     *
     * @return The timer number in seconds as an Int. E.g.: 180 means the timer is set for 3 minutes.
     */
    suspend fun getTimer(): Int {
        return TimerIO.request()
    }

    /**
     * Set Timer value in seconds.
     *
     * @param timerValue Timer number of seconds as an Int.  E.g.: 180 means the timer will be set for 3 minutes.
     */
    fun setTimer(timerValue: Int) {
        TimerIO.set(timerValue)
    }

    /**
     * Gets and internally sets app info to the watch.
     * This is needed to re-enable lower-right button after the watch has been reset or BLE has been cleared.
     * Call this function after each time the connection has been established.
     *
     * @return appInfo string from the watch.
     */
    suspend fun getAppInfo(): String {
        return AppInfoIO.request()
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
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun setTime(timeZone: String? = null) {
        TimeIO.set(timeZone)
    }

    /**
     * Gets the current alarms from the watch. Up to 5 alarms are supported on the watch.
     *
     * @return ArrayList<[Alarm]>
     */

    suspend fun getAlarms(): ArrayList<Alarm> {
        return AlarmsIO.request()
    }

    /**
     * Sets alarms to the watch. Up to 5 alarms are supported on the watch.
     *
     * @param ArrayList<[Alarm]>
     */
    fun setAlarms(alarms: ArrayList<Alarm>) {
        AlarmsIO.set(alarms)
    }

    /**
     * Gets the current events (reminders) from the watch. Up to 5 events are supported.
     *
     * @return ArrayList<[Event]>
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventsFromWatch(): ArrayList<Event> {

        val events = ArrayList<Event>()

        events.add(EventsIO.request(1))
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
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventFromWatch(eventNumber: Int): Event {
        return EventsIO.request(eventNumber)
    }

    /**
     * Sets events (reminders) to the watch. Up to 5 events are supported.
     *
     * @param ArrayList<[Event]>
     */
    fun setEvents(events: ArrayList<Event>) {
        EventsIO.set(events)
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

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getSettings(): Settings {
        val settings = getBasicSettings()
        val timeAdjustment = getTimeAdjustment()
        settings.timeAdjustment = timeAdjustment
        return settings
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun getBasicSettings(): Settings {
        return SettingsIO.request()
    }

    private suspend fun getTimeAdjustment(): Boolean {
        return TimeAdjustmentIO.request()
    }


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
    fun setSettings(settings: Settings) {
        SettingsIO.set(settings)
        TimeAdjustmentIO.set(settings)
    }

    /**
     * Get the Bluetooth ID of the connected watch
     *
     * @return watch's Bluetooth ID as a String. Should look something like: "ED:85:83:38:62:17"
     */
    fun getDeviceId(): String? {
        return Connection.getDeviceId()
    }

    /**
     * Disconnect from the watch
     *
     * @param context [Context]
     */
    fun disconnect(context: Context) {
        Connection.disconnect(context)
    }

    fun stopScan() {
        bleScannerLocal?.stopBleScan()
    }

    fun preventReconnection() {
        Connection.oneTimeLock = true
    }

    private fun setHomeTime(id: String) {
        HomeTimeIO.set(id)
    }

    /**
     * Tells us if Bluetooth is currently enabled on the phone. If not, the app can take action to enable it.
     *
     * @return *true* if enables, *false* otherwise.
     */
    fun isBluetoothEnabled(): Boolean {
        return bleScannerLocal.bluetoothAdapter.isEnabled
    }

    fun resetHand() {
        sendMessage("{action: \"RESET_HAND\", value: \"\"}")
    }
}