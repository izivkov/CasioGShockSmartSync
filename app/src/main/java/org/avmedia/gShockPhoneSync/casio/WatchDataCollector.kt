/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gShockPhoneSync.casio

import android.annotation.SuppressLint
import android.util.Log
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.json.JSONObject
import java.util.*
import java.util.Collections.addAll
import kotlin.collections.ArrayList
import kotlin.reflect.KFunction1

/*
 In BLE, handles are 16 bit values mapping to attributes UUID's like this: 26eb002d-b012-49a8-b1f8-394fb2032b0f
 We use two handles, 0xC (to ask for some value) and 0xE (to write a new value). Instead of using the
 long attribute UUID, we use the handle to look up the long value.
 You can read more here: https://www.oreilly.com/library/view/getting-started-with/9781491900550/ch04.html

We use handle 0xC to request specific information form the watch. For example, to obtains watch's name, we send
value 0x23 using handle 0xC like this:

    writeCmdWithResponseCount(0xC, "23")

The data will come in the callback Connection.onCharacteristicChanged(), which calls our provided callback
dataReceived() in WatchDataListener class, and is further processed from there. The data will be sent to
different subscribers, listening on topics of interest. The topics correspond to the first two bytes of
the data, i.e. "23" for watch name. So, when we call:

    subscribe("CASIO_WATCH_NAME", ::onDataReceived)

any data starting with "23" will be received in the onDataReceived() method.

To update value on the watch, we use handle 0xE, like this:

    // update first World City:

    writeCmd(0xE, "1f00544f524f4e544f00000000000000000000000000")

        1f - update city
        00 - city number - 00 means the first city
        54 4f 52 4f 4e 54 4f... - city name, to TORONTO in ascii
*/

object WatchDataCollector {

    private val worldCities: HashMap<Int, CasioTimeZone.WorldCity> =
        HashMap<Int, CasioTimeZone.WorldCity>()

    var batteryLevel: Int = 0
    var watchName: String = ""
    var moduleId: String = ""
    var homeCity: String = ""
    var bleFeatures: String = ""

    /*
    1. Request data from watch by sending writeCmd(0xC, request)
    2. Receive corresponding data via onDataReceived() and match to request
    3. Send back this result to the watch. This is needed in order to be able set time.
     */
    class DataItem(
        var request: String,
        var replyAction: KFunction1<String, Unit>,
        var waitingForReply: Boolean = true
    ) {
        var response: String = ""

        init {
            request = request.uppercase()
            writeCmd(0xC, request)
        }
    }

    private fun initShortList(): List<DataItem> {
        val list = ArrayList<DataItem>()

        // ble features
        list.add(DataItem("10", ::setBleFeatures1))
        return list
    }

    private fun initLongList(): List<DataItem> {
        val list = ArrayList<DataItem>()

        Connection.enableNotifications()

        // ble features
        list.add(DataItem("10", ::setBleFeatures1))

        // get DTS watch state
        list.add(DataItem("1d00", ::sendDataToWatch))
        list.add(DataItem("1d02", ::sendDataToWatch))
        list.add(DataItem("1d04", ::sendDataToWatch))

        // get DTS for world cities
        list.add(DataItem("1e00", ::setHomeCity1))
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

        list.add(DataItem("23", ::setWatchName1))
        list.add(DataItem("28", ::setBatteryLevel, false))
        list.add(DataItem("22", ::setAppInfo1))

        return list
    }

    private val itemList = ArrayList<DataItem>()
    private lateinit var itemMap:Map <String, DataItem>

    init {
        subscribe("CASIO_BLE_FEATURES", ::onDataReceived)
        subscribe("CASIO_DST_SETTING", ::onDataReceived)
        subscribe("CASIO_WORLD_CITIES", ::onDataReceived)
        subscribe("CASIO_DST_WATCH_STATE", ::onDataReceived)
        subscribe("CASIO_WATCH_NAME", ::onDataReceived)
        subscribe("CASIO_WATCH_CONDITION", ::onDataReceived)
        subscribe("CASIO_APP_INFORMATION", ::onDataReceived)
    }

    fun start() {
        itemList.addAll(initLongList())
        itemMap = itemList.associateBy { it.request }.toMap()
    }

    private fun requestButtonPressedInformation() {
        // CASIO_BLE_FEATURES, determine which button was pressed from these.
        writeCmd(0xC, "10")
    }

    private fun onDataReceived(data: String) {
        val shortStr = Utils.toCompactString(data)

        var keyLength = 2
        // get the first byte of the returned data, which indicates the data content.
        val startOfData = shortStr.substring(0, 2).uppercase(Locale.getDefault())
        if (startOfData in arrayOf("1D", "1E", "1F")) {
            keyLength = 4
        }

        val cmdKey = shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
        val dataItem = itemMap[cmdKey]
        dataItem?.waitingForReply = false
        dataItem?.replyAction?.let { it(data) }

        if (isComplete()) {
            ProgressEvents.onNext(ProgressEvents.Events.WatchDataCollected)
            ProgressEvents.onNext(ProgressEvents.Events.WatchInitializationCompleted)
        }
    }

    fun toJson(command: String): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("WATCH_INFO_DATA", command)
        return jsonObject
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
        watchName = Utils.toAsciiString(data, 1)
    }

    private fun setBatteryLevel(data: String): Unit {
        // battery level, i.e. 28132400
        batteryLevel = BatteryLevelDecoder.decodeValue(data).toInt()
    }

    private fun setHomeCity1(data: String): Unit {
        val shortStr = Utils.toCompactString(data)
        val wc = createWordCity(shortStr)
        worldCities[wc.index] = wc // replace existing element if it exists
        if (homeCity == "") {
            // Home city is in the fist position, so data will start with "1F 00"
            val cityNumber = shortStr.substring(2, 4)
            if (cityNumber.toInt() == 0) {
                homeCity = Utils.toAsciiString(data, 2)
            }
        }
    }

    private fun setBleFeatures1(data: String): Unit {
        bleFeatures = data
        ProgressEvents.onNext(ProgressEvents.Events.ButtonPressedInfoReceived)
    }

    private fun setAppInfo1(data: String): Unit {
        // app info
        // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
        // It is a hard-coded value, which is what the official app does as well.

        writeCmd(0xE, "223488F4E5D5AFC829E06D02")
    }
}