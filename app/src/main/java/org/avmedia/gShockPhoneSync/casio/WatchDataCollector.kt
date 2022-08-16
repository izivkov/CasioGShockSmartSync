/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gShockPhoneSync.casio

import android.annotation.SuppressLint
import android.app.Notification
import android.util.Log
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ui.actions.ActionsModel
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KFunction1

/*
1. Request data from watch by sending writeCmd(0xC, request)
2. Receive corresponding data via onDataReceived().
3. Send back this result to the watch. This is needed in order to be able to set time.

=====================================================================
 In BLE, handles are 16 bit values mapping to attributes UUID's like this: 26eb002d-b012-49a8-b1f8-394fb2032b0f
 We use two handles, 0xC (to ask for some value) and 0xE (to write a new value). Instead of using the
 long attribute UUID, we use the handle to look up the long value.
 You can read more here: https://www.oreilly.com/library/view/getting-started-with/9781491900550/ch04.html

We use handle 0xC to request specific information form the watch. For example, to obtains watch's name, we send
value 0x23 using handle 0xC like this:

    writeCmd(0xC, "23")
 */

object WatchDataCollector {

    private val worldCities: HashMap<Int, CasioTimeZone.WorldCity> =
        HashMap<Int, CasioTimeZone.WorldCity>()

    var watchNameValue: String = ""
    var homeCityValue: String = ""
    var bleFeaturesValue: String = ""

    class DataItem(
        var request: String,
        var responseAction: KFunction1<String, Unit>,
        var waitingForReply: Boolean = true
    ) {
        var response: String = ""

        init {
            request = request.uppercase()
        }

        fun request() {
            writeCmd(0xC, request)
        }
    }

    private val itemList: List<DataItem> by lazy(::initList)
    private val itemMap by lazy {
        itemList.associateBy { it.request }.toMap()
    }

    init {
        subscribe("CASIO_BLE_FEATURES", ::onDataReceived)
        subscribe("CASIO_DST_SETTING", ::onDataReceived)
        subscribe("CASIO_WORLD_CITIES", ::onDataReceived)
        subscribe("CASIO_DST_WATCH_STATE", ::onDataReceived)
        subscribe("CASIO_WATCH_NAME", ::onDataReceived)
        subscribe("CASIO_APP_INFORMATION", ::onDataReceived)
    }

    fun start() {
        Connection.enableNotifications()
        requestButtonPressedInformation()
    }

    private fun requestButtonPressedInformation() {
        // CASIO_BLE_FEATURES, determine which button was pressed from these.
        sendRequests(itemList.filter { it.request == "10" })
    }

    private fun setBleFeatures(data: String): Unit {
        bleFeaturesValue = data
        ProgressEvents.onNext(ProgressEvents.Events.ButtonPressedInfoReceived)
        sendRequests(filterItems(itemList))
    }

    private fun filterItems (_itemList:List<DataItem>): List<DataItem> {
        // remove the item we have already processed
        var items = _itemList.filter { it.request != "10" }

        if (WatchFactory.watch.isActionButtonPressed()) {
            if (!ActionsModel.hasTimeSet()) {
                // We are running actions, and none of them has to set time...
                // We do not need to initialise watch. Return empty list.
                return ArrayList<DataItem>()
            }

            // If we are running actions, we do not need to batteryLevel.
            return items.filter { it.request != "28" }
        }
        return items
    }

    private fun initList(): List<DataItem> {
        val list = ArrayList<DataItem>()

        // ble features - used to determine which button on the watch was pressed.
        list.add(DataItem("10", ::setBleFeatures))

        // get DTS watch state
        list.add(DataItem("1d00", ::sendDataToWatch))
        list.add(DataItem("1d02", ::sendDataToWatch))
        list.add(DataItem("1d04", ::sendDataToWatch))

        // get DTS for world cities
        list.add(DataItem("1e00", ::setHomeCity))
        list.add(DataItem("1e01", ::sendDataToWatch))
        list.add(DataItem("1e02", ::sendDataToWatch))
        list.add(DataItem("1e03", ::sendDataToWatch))
        list.add(DataItem("1e04", ::sendDataToWatch))
        list.add(DataItem("1e05", ::sendDataToWatch))

        // World cities
        list.add(DataItem("1f00", ::sendDataToWatch))
        list.add(DataItem("1f01", ::sendDataToWatch))
        list.add(DataItem("1f02", ::sendDataToWatch))
        list.add(DataItem("1f03", ::sendDataToWatch))
        list.add(DataItem("1f04", ::sendDataToWatch))
        list.add(DataItem("1f05", ::sendDataToWatch))

        // watch name, i.e. 23434153494f2047572d42353630300000000000
        list.add(DataItem("23", ::setWatchName1))

        // Battery level, i.e. 28132400.
        // Do not expect result from setBatteryLevel.
        list.add(DataItem("28", ::setBatteryLevel, false))

        // app info
        // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
        // It is a hard-coded value, which is what the official app does as well.
        list.add(DataItem("22", ::setAppInfo))

        return list
    }

    private fun sendRequests(_itemList: List<DataItem>) {
        _itemList.forEach {

            // do not expect result from setBatteryLevel
            it.waitingForReply = it.request != "28"

            it.request()
        }
    }

    private fun onDataReceived(data: String) {

        // we sometimes get unknown data, like 100. Just ignore it.
        if (data.length <= 3) {
            return
        }

        val shortStr = Utils.toCompactString(data)
        var keyLength = 2
        // get the first byte of the returned data, which indicates the data content.
        val startOfData = shortStr.substring(0, 2).uppercase(Locale.getDefault())
        if (startOfData in arrayOf("1D", "1E", "1F")) {
            keyLength = 4
        }

        val cmdKey = shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
        val dataItem: DataItem? = itemMap[cmdKey]
        dataItem?.waitingForReply = false
        dataItem?.response = data

        // run the response action
        dataItem?.responseAction?.let { it(data) }
    }

    private fun createWordCity(casioString: String): CasioTimeZone.WorldCity {
        val city = Utils.toAsciiString(casioString.substring(4).trim('0'), 0)
        val index = casioString.substring(2, 4).toInt()
        return CasioTimeZone.WorldCity(city, index)
    }

    @SuppressLint("CheckResult")
    private fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        WatchDataEvents.addSubject(subject)

        // receive values from the commands we issued in start()
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject, onNext = {

            onDataReceived(it as String)

            if (isComplete()) {
                ProgressEvents.onNext(ProgressEvents.Events.WatchDataCollected)
                ProgressEvents.onNext(ProgressEvents.Events.WatchInitializationCompleted)
            }
        })
    }

    fun rereadHomeTimeFromWatch() {
        WatchFactory.watch.writeCmdFromString(0xC, "1f00")
    }

    fun setHomeTime(worldCityName: String) {
        WatchFactory.watch.writeCmdFromString(0xe, worldCityName)
    }

    private fun writeCmd(handle: Int, cmd: String) {
        WatchFactory.watch.writeCmdFromString(handle, cmd)
    }

    private fun isComplete(): Boolean {
        return itemList.none { it.waitingForReply }
    }

    ////////////////////
    // Response actions.
    ////////////////////
    private fun sendDataToWatch(data: String): Unit {
        writeCmd(0xE, Utils.toCompactString(data))
    }

    private fun setWatchName1(data: String): Unit {
        // watch name, i.e. 23434153494f2047572d42353630300000000000
        watchNameValue = Utils.toAsciiString(data, 1)
    }

    private fun setBatteryLevel(data: String): Unit {
        // empty
    }

    private fun setHomeCity(data: String): Unit {
        val shortStr = Utils.toCompactString(data)
        val wc = createWordCity(shortStr)
        worldCities[wc.index] = wc // replace existing element if it exists
        if (homeCityValue == "") {
            // Home city is in the fist position, so data will start with "1F 00"
            val cityNumber = shortStr.substring(2, 4)
            if (cityNumber.toInt() == 0) {
                homeCityValue = Utils.toAsciiString(data, 2)
            }
        }

        writeCmd(0xE, Utils.toCompactString(data))
    }

    private fun setAppInfo(data: String): Unit {
        // App info:
        // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
        // It is a hard-coded value, which is what the official app does as well.

        // If watch was reset, the app info will come as:
        // 0x22 FF FF FF FF FF FF FF FF FF FF 00
        // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
        val appInfoCompactStr = Utils.toCompactString(data)
        if (appInfoCompactStr == "22FFFFFFFFFFFFFFFFFFFF00") {
            writeCmd(0xE, "223488F4E5D5AFC829E06D02")
        }
    }
}