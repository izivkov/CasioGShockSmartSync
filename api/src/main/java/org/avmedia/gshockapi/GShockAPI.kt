package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ProgressEvents.lookupEvent
import org.avmedia.gshockapi.ble.BleScannerLocal
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.Connection.sendMessage
import org.avmedia.gshockapi.ble.DeviceCharacteristics
import org.avmedia.gshockapi.casio.*
import org.avmedia.gshockapi.casio.CasioIO.request
import org.avmedia.gshockapi.utils.*
import org.avmedia.gshockapi.utils.Utils.getBooleanSafe
import org.json.JSONObject
import timber.log.Timber
import java.time.Clock
import java.util.*
import kotlin.reflect.KSuspendFunction1

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
    private val cache = WatchValuesCache()

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

    suspend fun waitForConnection(deviceId: String? = ""): String {
        var connectedStatus = _waitForConnection(deviceId)
        init()
        return connectedStatus
    }

    private suspend fun _waitForConnection(deviceId: String? = ""): String {

        if (Connection.isConnected() || Connection.isConnecting()) {
            return "Connecting"
        }

        Connection.init(context)
        WatchDataListener.init()

        bleScannerLocal = BleScannerLocal(context)
        bleScannerLocal.startConnection(deviceId)

        val deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        fun waitForConnectionSetupComplete() {
            ProgressEvents.subscriber.start(this.javaClass.simpleName, {
                when (it) {
                    lookupEvent("ConnectionSetupComplete") -> {
                        val device =
                            ProgressEvents.getPayload("ConnectionSetupComplete") as BluetoothDevice
                        DeviceCharacteristics.init(device)

                        resultQueue.dequeue()?.complete("Connected")
                    }
                }
            }, { throwable ->
                Timber.d("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
        }

        waitForConnectionSetupComplete()

        return deferredResult.await()
    }

    private suspend fun init() {
        WatchFactory.watch.init()
        resultQueue.clear()
        getPressedButton()

        ProgressEvents.onNext("ButtonPressedInfoReceived")
        getAppInfo() // this call re-enables lower-right button after watch reset.
        ProgressEvents.onNext("WatchInitializationCompleted")
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
     * @see BluetoothWatch.WATCH_BUTTON
     */
    /* Do not get value from cache, because we do not want to
    get all values here. */
    suspend fun getPressedButton(): BluetoothWatch.WATCH_BUTTON {
        val key = "10"
        val ret = _getPressedButton(key)
        cache.put(key, ret) // store in cache for functions such as isActionButtonPressed()
        return ret
    }

    private suspend fun _getPressedButton(key: String): BluetoothWatch.WATCH_BUTTON {

        request(key)

        val deferredResultButton = CompletableDeferred<BluetoothWatch.WATCH_BUTTON>()
        resultQueue.enqueue(deferredResultButton as CompletableDeferred<Any>)

        subscribe("BUTTON_PRESSED") { data ->
            /*
            RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
            LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
                          0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
            AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00 // no button pressed
            */
            var ret: BluetoothWatch.WATCH_BUTTON = BluetoothWatch.WATCH_BUTTON.INVALID

            if (data != "" && Utils.toIntArray(data).size >= 19) {
                val bleIntArr = Utils.toIntArray(data)
                ret = when (bleIntArr[8]) {
                    in 0..1 -> BluetoothWatch.WATCH_BUTTON.LOWER_LEFT
                    4 -> BluetoothWatch.WATCH_BUTTON.LOWER_RIGHT
                    3 -> BluetoothWatch.WATCH_BUTTON.NO_BUTTON // auto time set, no button pressed. Run actions to set time and calender only.
                    else -> BluetoothWatch.WATCH_BUTTON.INVALID
                }
            }
            resultQueue.dequeue()!!.complete(ret)
        }

        return deferredResultButton.await()
    }

    /**
     * This function tells us if the connection was initiated by short-pressing the lower-right button on the
     * watch, also known as ACTION BUTTON
     *
     * @return **true** if the lower-right button initiated the connection, **false** otherwise.
     */
    fun isActionButtonPressed(): Boolean {
        val button = cache.get("10") as BluetoothWatch.WATCH_BUTTON
        return button == BluetoothWatch.WATCH_BUTTON.LOWER_RIGHT
    }

    /**
     * This function tells us if the connection was initiated by long-pressing the lower-left
     * button on the watch
     *
     * @return **true** if the lower-left button initiated the connection, **false** otherwise.
     */
    fun isNormalButtonPressed(): Boolean {
        val button = cache.get("10") as BluetoothWatch.WATCH_BUTTON
        return button == BluetoothWatch.WATCH_BUTTON.LOWER_LEFT
    }

    /**
     * This function tells us if the connection was initiated automatically by the watch, without the user
     * pressing any button. This happens if Auto-Time is set in the setting. In this case, the
     * watch will periodically connect at around 00:30, 06:30, 12:30 and 18:30
     *
     * @return **true** if watch automatically initiated the connection, **false** otherwise.
     */
    fun isAutoTimeStarted(): Boolean {
        val button = cache.get("10") as BluetoothWatch.WATCH_BUTTON
        return button == BluetoothWatch.WATCH_BUTTON.NO_BUTTON
    }

    /**
     * Get the name of the watch.
     *
     * @return returns the name of the watch as a String. i.e. "GW-B5600"
     */
    suspend fun getWatchName(): String {
        val key = "23"
        return cache.getCached(key, ::_getWatchName) as String
    }

    private suspend fun _getWatchName(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_WATCH_NAME") {
            resultQueue.dequeue()
                ?.complete(Utils.trimNonAsciiCharacters(Utils.toAsciiString(it, 1)))
        }

        return deferredResult.await()
    }

    /**
     * Get the DST state of the watch.
     *
     * @return returns the Daylight Saving Time state of the watch as a String.
     */
    suspend fun getDSTWatchState(state: BluetoothWatch.DTS_STATE): String {
        val key = "1d0${state.state}"
        return cache.getCached(key, ::_getDSTWatchState) as String
    }

    private suspend fun _getDSTWatchState(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_DST_WATCH_STATE") { data: String ->
            resultQueue.dequeue()?.complete(data)
        }

        return deferredResult.await()
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
        val key = "1e0$cityNumber"
        return cache.getCached(key, ::_getDSTForWorldCities) as String
    }

    private suspend fun _getDSTForWorldCities(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_DST_SETTING") { data: String ->
            resultQueue.dequeue()?.complete(data)
        }

        return deferredResult.await()
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
        val key = "1f0$cityNumber"
        return cache.getCached(key, ::_getWorldCities) as String
    }

    private suspend fun _getWorldCities(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_WORLD_CITIES") { data: String ->
            resultQueue.dequeue()?.complete(data)
        }

        return deferredResult.await()
    }

    /**
     * Get Home Time, (Home City).
     *
     * @return The name of Home City as a String.
     */
    suspend fun getHomeTime(): String {
        val homeCityRaw = cache.getCached(
            "1f00", ::_getWorldCities
        ) as String // get home time from the first city in the list

        return Utils.toAsciiString(homeCityRaw, 2)
    }

    /**
     * Get Battery level.
     *
     * @return the battery level in percent as a String. E.g.: "83"
     */
    suspend fun getBatteryLevel(): String {
        return cache.getCached("28", ::_getBatteryLevel) as String
    }

    private suspend fun _getBatteryLevel(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_WATCH_CONDITION") {
            resultQueue.dequeue()?.complete(BatteryLevelDecoder.decodeValue(it))
        }

        return deferredResult.await()
    }

    /**
     * Get Timer value in seconds.
     *
     * @return The timer number in seconds as an Int.  E.g.: 180 means the timer is set for 3 minutes.
     */
    suspend fun getTimer(): Int {
        return cache.getCached("18", ::_getTimer) as Int
    }

    private suspend fun _getTimer(key: String): Int {

        request(key)

        fun getTimer(data: String): String {
            return TimerDecoder.decodeValue(data)
        }

        var deferredResult = CompletableDeferred<Int>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_TIMER") {
            resultQueue.dequeue()?.complete(getTimer(it).toInt())
        }

        return deferredResult.await()
    }


    /**
     * Set Timer value in seconds.
     *
     * @param timerValue Timer number of seconds as an Int.  E.g.: 180 means the timer will be set for 3 minutes.
     */
    fun setTimer(timerValue: Int) {
        cache.remove("18")
        sendMessage("{action: \"SET_TIMER\", value: $timerValue}")
    }

    /**
     * Gets and internally sets app info to the watch.
     * This is needed to re-enable lower-right button after the watch has been reset or BLE has been cleared.
     * Call this function after each time the connection has been established.
     *
     * @return appInfo string from the watch.
     */
    suspend fun getAppInfo(): String {
        return cache.getCached("22", ::_getAppInfo) as String
    }

    private suspend fun _getAppInfo(key: String): String {

        request(key)

        fun setAppInfo(data: String): Unit {
            // App info:
            // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
            // It is a hard-coded value, which is what the official app does as well.

            // If watch was reset, the app info will come as:
            // 0x22 FF FF FF FF FF FF FF FF FF FF 00
            // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
            val appInfoCompactStr = Utils.toCompactString(data)
            if (appInfoCompactStr == "22FFFFFFFFFFFFFFFFFFFF00") {
                CasioIO.writeCmd(0xE, "223488F4E5D5AFC829E06D02")
            }
        }

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_APP_INFORMATION") {
            resultQueue.dequeue()?.complete(it)
            setAppInfo(it)
        }

        return deferredResult.await()
    }

    /**
     * Sets the current time on the watch from the time on the phone. In addition, it can optionally set the Home Time
     * to the current time zone. If timezone changes during travel, the watch will automatically be set to the
     * correct time and timezone after running this function.
     *
     * @param changeHomeTime If *true*, the **Home Time** will be changed to the current timezone.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun setTime(changeHomeTime: Boolean = true) {

        initializeForSettingTime()

        // Update the HomeTime according to the current TimeZone
        // This could be optimised to be called only if the
        // timezone has changed, but this adds complexity.
        // Maybe we can do this in the future.
        if (changeHomeTime) {
            CasioTimeZone.setHomeTime(TimeZone.getDefault().id)
            setHomeTime(TimeZone.getDefault().id)
        }

        sendMessage(
            "{action: \"SET_TIME\", value: ${
                Clock.systemDefaultZone().millis()
            }}"
        )
    }

    /**
     * This function is internally called by [setTime] to initialize some values.
     */
    private suspend fun initializeForSettingTime() {
        // Before we can set time, we must read and write back these values.
        // Why? Not sure, ask Casio

        suspend fun <T> readAndWrite(function: KSuspendFunction1<T, String>, param: T) {
            val ret: String = function(param)
            val shortStr = Utils.toCompactString(ret)
            CasioIO.writeCmd(0xE, shortStr)
        }

        readAndWrite(::getDSTWatchState, BluetoothWatch.DTS_STATE.ZERO)
        readAndWrite(::getDSTWatchState, BluetoothWatch.DTS_STATE.TWO)
        readAndWrite(::getDSTWatchState, BluetoothWatch.DTS_STATE.FOUR)

        readAndWrite(::getDSTForWorldCities, 0)
        readAndWrite(::getDSTForWorldCities, 1)
        readAndWrite(::getDSTForWorldCities, 2)
        readAndWrite(::getDSTForWorldCities, 3)
        readAndWrite(::getDSTForWorldCities, 4)
        readAndWrite(::getDSTForWorldCities, 5)

        readAndWrite(::getWorldCities, 0)
        readAndWrite(::getWorldCities, 1)
        readAndWrite(::getWorldCities, 2)
        readAndWrite(::getWorldCities, 3)
        readAndWrite(::getWorldCities, 4)
        readAndWrite(::getWorldCities, 5)
    }

    /**
     * Gets the current alarms from the watch. Up to 5 alarms are supported on the watch.
     *
     * @return ArrayList<[Alarm]>
     */
    suspend fun getAlarms(): ArrayList<Alarm> {
        var alarms = ArrayList<Alarm>()

        fun fromJson(jsonStr: String) {
            val gson = Gson()
            val alarmArr = gson.fromJson(jsonStr, Array<Alarm>::class.java)
            alarms.addAll(alarmArr)
        }

        sendMessage("{ action: 'GET_ALARMS'}")

        var deferredResult = CompletableDeferred<ArrayList<Alarm>>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("ALARMS") {
            fromJson(it)
            if (alarms.size > 1) {
                resultQueue.dequeue()?.complete(alarms)
            }
        }
        return deferredResult.await()
    }

    /**
     * Sets alarms to the watch. Up to 5 alarms are supported on the watch.
     *
     * @param ArrayList<[Alarm]>
     */
    fun setAlarms(alarms: ArrayList<Alarm>) {
        if (alarms.isEmpty()) {
            Timber.d("Alarm model not initialised! Cannot set alarm")
            return
        }

        @Synchronized
        fun toJson(): String {
            val gson = Gson()
            return gson.toJson(alarms)
        }

        sendMessage("{action: \"SET_ALARMS\", value: ${toJson()} }")
    }

    /**
     * Gets the current events (reminders) from the watch. Up to 5 events are supported.
     *
     * @return ArrayList<[Event]>
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventsFromWatch(): ArrayList<Event> {

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
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventFromWatch(eventNumber: Int): Event {
        request("30${eventNumber}") // reminder title
        request("31${eventNumber}") // reminder time

        var deferredResult = CompletableDeferred<Event>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        var title = ""
        subscribe("REMINDERS") {
            val reminderJson = JSONObject(it)
            when (reminderJson.keys().next()) {
                "title" -> {
                    title = reminderJson["title"] as String
                }
                "time" -> {
                    reminderJson.put("title", title)
                    val event = Event(reminderJson)
                    resultQueue.dequeue()?.complete(event)
                }
            }
        }
        return deferredResult.await()
    }

    /**
     * Sets events (reminders) to the watch. Up to 5 events are supported.
     *
     * @param ArrayList<[Event]>
     */
    fun setEvents(events: ArrayList<Event>) {

        if (events.isEmpty()) {
            Timber.d("Events model not initialised! Cannot set reminders")
            return
        }

        @Synchronized
        fun toJson(events: ArrayList<Event>): String {
            val gson = Gson()
            return gson.toJson(events)
        }

        fun getSelectedEvents(events: ArrayList<Event>): String {
            val selectedEvents = events.filter { it.selected } as ArrayList<Event>
            return toJson(selectedEvents)
        }

        sendMessage("{action: \"SET_REMINDERS\", value: ${getSelectedEvents(events)} }")
    }

    private fun subscribe(subject: String, onDataReceived: (String) -> Unit): Unit {
        WatchDataEvents.addSubject(subject)

        // receive values from the commands we issued in start()
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject) {
            onDataReceived(it as String)
        }
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
    suspend fun getSettings(): Settings {
        val settings = getBasicSettings()
        val timeAdjustment = getTimeAdjustment()
        settings.timeAdjustment = timeAdjustment
        return settings
    }

    private suspend fun getBasicSettings(): Settings {
        sendMessage("{ action: 'GET_SETTINGS'}")

        var deferredResult = CompletableDeferred<Settings>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("SETTINGS") {
            val model = Gson().fromJson(it, Settings::class.java)
            resultQueue.dequeue()?.complete(model)

        }
        return deferredResult.await()
    }

    private suspend fun getTimeAdjustment(): Boolean {
        sendMessage("{ action: 'GET_TIME_ADJUSTMENT'}")

        var deferredResult = CompletableDeferred<Boolean>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("TIME_ADJUSTMENT") { timeAdjustmentData ->
            val dataJson = JSONObject(timeAdjustmentData)
            val timeAdjustment = dataJson.getBooleanSafe("timeAdjustment") == true

            resultQueue.dequeue()?.complete(timeAdjustment)
        }

        return deferredResult.await()
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
        val settingJson = Gson().toJson(settings)
        sendMessage("{action: \"SET_SETTINGS\", value: ${settingJson}}")
        sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: ${settingJson}}")
    }


    /**
     * Get the Bluetooth ID os the connected watch
     *
     * @return watch's Bluetooth ID as a String. Should look something like: "ED:85:83:38:62:17"
     */
    fun getDeviceId(): String {
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

    fun preventReconnection() {
        Connection.oneTimeLock = true
    }

    private fun setHomeTime(id: String) {
        cache.remove("1f00")
        CasioTimeZone.setHomeTime(id)
    }

    /**
     * Tells us if Bluetooth is currently enabled on the phone. If not, the app can take action to enable it.
     *
     * @return *true* if enables, *false* otherwise.
     */
    fun isBluetoothEnabled(): Boolean {
        return bleScannerLocal.bluetoothAdapter.isEnabled
    }
}