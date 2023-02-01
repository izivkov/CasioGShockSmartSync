package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
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

class GShockAPI(private val context: Context) {

    private var bleScannerLocal: BleScannerLocal = BleScannerLocal(context)
    private val resultQueue = ResultQueue<CompletableDeferred<Any>>()
    private val cache = WatchValuesCache()

    suspend fun waitForConnection(deviceId: String? = ""): String {
        Connection.init(context)
        WatchDataListener.init()

        bleScannerLocal = BleScannerLocal(context)
        bleScannerLocal.startConnection(deviceId)
        Connection.init(context)

        val deferredResult = CompletableDeferred<String>()
        fun waitForConnectionSetupComplete() {
            ProgressEvents.subscriber.start(this.javaClass.simpleName, {
                when (it) {
                    ProgressEvents.Events.ConnectionSetupComplete -> {
                        val device =
                            ProgressEvents.Events.ConnectionSetupComplete.payload as BluetoothDevice

                        DeviceCharacteristics.init(device)
                        Connection.enableNotifications()
                        deferredResult.complete("Connected")
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

    suspend fun getPressedButton(): BluetoothWatch.WATCH_BUTTON {
        return cache.getCached("10", ::_getPressedButton) as BluetoothWatch.WATCH_BUTTON
    }

    private suspend fun _getPressedButton(key:String): BluetoothWatch.WATCH_BUTTON {

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
            var ret = BluetoothWatch.WATCH_BUTTON.INVALID

            if (data != "" && Utils.toIntArray(data).size >= 19) {
                val bleIntArr = Utils.toIntArray(data)
                ret = when (bleIntArr[8]) {
                    in 0..1 -> BluetoothWatch.WATCH_BUTTON.LOWER_LEFT
                    4 -> BluetoothWatch.WATCH_BUTTON.LOWER_RIGHT
                    3 -> BluetoothWatch.WATCH_BUTTON.NO_BUTTON // auto time set, no button pressed. Run actions to set time and calender only.
                    else -> BluetoothWatch.WATCH_BUTTON.INVALID
                }
            }

            resultQueue.dequeue()?.complete(ret)
        }

        return deferredResultButton.await()
    }

    suspend fun isActionRunRequested(): Boolean {
        val watchButtonPressed = getPressedButton()
        return watchButtonPressed == BluetoothWatch.WATCH_BUTTON.LOWER_RIGHT || watchButtonPressed == BluetoothWatch.WATCH_BUTTON.NO_BUTTON // automatic time set
    }

    suspend fun isActionButtonPressed(): Boolean {
        val watchButtonPressed = getPressedButton()
        return watchButtonPressed == BluetoothWatch.WATCH_BUTTON.LOWER_RIGHT
    }

//    override fun isAutoTimeStarted(): Boolean {
//        val bleIntArr = Utils.toIntArray(WatchDataCollector.CollectedData.bleFeaturesValue)
//        if (bleIntArr.size < 19) {
//            return false
//        }
//
//        return bleIntArr[8] == 3
//    }

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

    suspend fun getDTSWatchState(state: BluetoothWatch.DTS_STATE): String {
        val key = "1d0${state.state}"
        return cache.getCached(key, ::_getDTSWatchState) as String
    }

    private suspend fun _getDTSWatchState(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_DST_WATCH_STATE") { data: String ->
            resultQueue.dequeue()?.complete(data)
        }

        return deferredResult.await()
    }

    suspend fun getDTSForWorldCities(cityNumber: Int): String {
        val key = "1e0$cityNumber"
        return cache.getCached(key, ::_getDTSForWorldCities) as String
    }

    private suspend fun _getDTSForWorldCities(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_DST_SETTING") { data: String ->
            resultQueue.dequeue()?.complete(data)
        }

        return deferredResult.await()
    }

    suspend fun getWorldCities(cityNumber: Int): Any {
        val key = "1f0$cityNumber"
        return cache.getCached(key, ::_getWorldCities)
    }

    private suspend fun _getWorldCities(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_WORLD_CITIES") { data: String ->

            val city = Utils.toAsciiString(data, 2)

            if (Utils.toIntArray(data)[1] == 0) { // set home_time from the first city
                cache.put("10", city)
            }

            resultQueue.dequeue()?.complete(city)
        }

        return deferredResult.await()
    }

    suspend fun getHomeTime(): String {
        return cache.getCached("1f00", ::_getWorldCities) as String // het home time from the first city in the list
    }

    suspend fun getBatteryLevel(): String {
        return cache.getCached("28", ::_getBatteryLevel) as String
    }

    suspend fun _getBatteryLevel(key: String): String {

        request(key)

        var deferredResult = CompletableDeferred<String>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_WATCH_CONDITION") {
            resultQueue.dequeue()?.complete(BatteryLevelDecoder.decodeValue(it))
        }

        return deferredResult.await()
    }

    suspend fun getTimer(): Long {
        return cache.getCached("18", ::_getTimer) as Long
    }

    private suspend fun _getTimer(key:String): Long {

        request(key)

        fun getTimer(data: String): String {
            return TimerDecoder.decodeValue(data)
        }

        var deferredResult = CompletableDeferred<Long>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("CASIO_TIMER") {
            resultQueue.dequeue()?.complete(getTimer(it).toLong())
        }

        return deferredResult.await()
    }


    fun setTimer(timerValue: Long) {
        sendMessage("{action: \"SET_TIMER\", value: $timerValue}")
    }

    suspend fun getAppInfo(): String {
        return cache.getCached("22", ::_getAppInfo) as String
    }

    private suspend fun _getAppInfo(key:String): String {

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

    @RequiresApi(Build.VERSION_CODES.O)
    fun setTime(changeHomeTime: Boolean = true) {

        // Update the HomeTime according to the current TimeZone
        // This could be optimised to be called only if the
        // timezone has changed, but this adds complexity.
        // Maybe we can do this in the future.
        if (changeHomeTime) {
            CasioTimeZone.setHomeTime(TimeZone.getDefault().id)
        }

        sendMessage(
            "{action: \"SET_TIME\", value: ${
                Clock.systemDefaultZone().millis() + (0 * 60 * 1000)
            }}"
        )
    }

    suspend fun init() {
        getDTSWatchState(BluetoothWatch.DTS_STATE.ZERO)
        getDTSWatchState(BluetoothWatch.DTS_STATE.TWO)
        getDTSWatchState(BluetoothWatch.DTS_STATE.FOUR)

        getDTSForWorldCities(0)
        getDTSForWorldCities(1)
        getDTSForWorldCities(2)
        getDTSForWorldCities(3)
        getDTSForWorldCities(4)
        getDTSForWorldCities(5)

        getWorldCities(0)
        getWorldCities(1)
        getWorldCities(2)
        getWorldCities(3)
        getWorldCities(4)
        getWorldCities(5)
    }

    suspend fun getAlarms(): AlarmsModel {
        var alarmModel = AlarmsModel
        alarmModel.clear()

        sendMessage("{ action: 'GET_ALARMS'}")

        var deferredResult = CompletableDeferred<AlarmsModel>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("ALARMS") {
            alarmModel.fromJson(it)
            if (alarmModel.alarms.size > 1) {
                resultQueue.dequeue()?.complete(alarmModel)
            }
        }
        return deferredResult.await()
    }

    fun setAlarms(alarmModel: AlarmsModel) {
        if (alarmModel.alarms.isEmpty()) {
            Timber.d("Alarm model not initialised! Cannot set alarm")
            return
        }
        sendMessage("{action: \"SET_ALARMS\", value: ${alarmModel.toJson()} }")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventsFromWatch(): ArrayList<EventsModel.Event> {

        val events = ArrayList<EventsModel.Event>()

        events.add(getEventFromWatch(1))
        events.add(getEventFromWatch(2))
        events.add(getEventFromWatch(3))
        events.add(getEventFromWatch(4))
        events.add(getEventFromWatch(5))

        return events
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventFromWatch(eventNumber: Int): EventsModel.Event {
        request("30${eventNumber}") // reminder title
        request("31${eventNumber}") // reminder time

        var deferredResult = CompletableDeferred<EventsModel.Event>()
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
                    val event = EventsModel.createEvent(reminderJson)
                    resultQueue.dequeue()?.complete(event)
                }
            }
        }
        return deferredResult.await()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getEventsFromCalendar(context: Context): ArrayList<EventsModel.Event> {
        return CalenderEvents.getDataFromEventTable(context)
    }

    fun setEvents(eventModel: EventsModel) {

        if (eventModel.isEmpty()) {
            Timber.d("Events model not initialised! Cannot set reminders")
            return
        }
        sendMessage("{action: \"SET_REMINDERS\", value: ${eventModel.getSelectedEvents()} }")
    }

    private fun subscribe(subject: String, onDataReceived: (String) -> Unit): Unit {
        WatchDataEvents.addSubject(subject)

        // receive values from the commands we issued in start()
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject) {
            onDataReceived(it as String)
        }
    }

    suspend fun getSettings(): SettingsSimpleModel {
        val model = SettingsModel

        sendMessage("{ action: 'GET_SETTINGS'}")

        var deferredResult = CompletableDeferred<SettingsSimpleModel>()
        resultQueue.enqueue(deferredResult as CompletableDeferred<Any>)

        subscribe("SETTINGS") {
            val settingsModel = SettingsModel
            settingsModel.fromJson(it)

            val settingsSimpleModel = SettingsSimpleModel()

            val localeSetting = SettingsModel.locale as SettingsModel.Locale
            localeSetting.dayOfWeekLanguage.value = settingsSimpleModel.language
            settingsSimpleModel.timeFormat = localeSetting.timeFormat.value
            settingsSimpleModel.dateFormat = localeSetting.dateFormat.value

            val lightSetting = settingsModel.light as SettingsModel.Light
            settingsSimpleModel.autoLight = lightSetting.autoLight
            settingsSimpleModel.lightDuration = lightSetting.duration.value

            val powerSavingMode = settingsModel.powerSavingMode as SettingsModel.PowerSavingMode
            settingsSimpleModel.powerSavingMode = powerSavingMode.powerSavingMode

            val timeAdjustment = settingsModel.timeAdjustment as SettingsModel.TimeAdjustment
            settingsSimpleModel.timeAdjustment = timeAdjustment.timeAdjustment

            val buttonTone = model.buttonSound as SettingsModel.OperationSound
            settingsSimpleModel.buttonTone = buttonTone.sound

            sendMessage("{ action: 'GET_TIME_ADJUSTMENT'}")
            subscribe("TIME_ADJUSTMENT") { timeAdjustmentData ->
                val dataJson = JSONObject(timeAdjustmentData)
                settingsSimpleModel.timeAdjustment =
                    dataJson.getBooleanSafe("timeAdjustment") == true
                resultQueue.dequeue()?.complete(settingsSimpleModel)
            }
        }

        return deferredResult.await()
    }

    fun setSettings(settingsTransferObj: SettingsSimpleModel) {
        val settingJson = Gson().toJson(settingsTransferObj)
        sendMessage("{action: \"SET_SETTINGS\", value: ${settingJson}}")
        sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: ${settingJson}}")
    }

    fun getDeviceId(): String {
        return Connection.getDeviceId()
    }
}