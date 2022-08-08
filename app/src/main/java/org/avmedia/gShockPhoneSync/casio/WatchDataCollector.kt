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
import kotlin.collections.HashMap

/* In BLE, handles are 16 bit values mapping to attributes UUID's like this: 26eb002d-b012-49a8-b1f8-394fb2032b0f
 We use two handles, 0xC (to ask for some value) and 0xE (to write a new value). Instead of using the
 long attribute UUID, we use the handle to look up the long value.
 You can read more here: https://www.oreilly.com/library/view/getting-started-with/9781491900550/ch04.html

Here us a table of standard handles and corresponding UUID:
handle: 0x0003, char properties: 0x02, char value handle: 0x0004, uuid: 00002a00-0000-1000-8000-00805f9b34fb
handle: 0x0005, char properties: 0x02, char value handle: 0x0006, uuid: 00002a01-0000-1000-8000-00805f9b34fb
handle: 0x0008, char properties: 0x02, char value handle: 0x0009, uuid: 00002a07-0000-1000-8000-00805f9b34fb
handle: 0x000b, char properties: 0x04, char value handle: 0x000c, uuid: 26eb002c-b012-49a8-b1f8-394fb2032b0f
handle: 0x000d, char properties: 0x18, char value handle: 0x000e, uuid: 26eb002d-b012-49a8-b1f8-394fb2032b0f
handle: 0x0010, char properties: 0x18, char value handle: 0x0011, uuid: 26eb0023-b012-49a8-b1f8-394fb2032b0f
handle: 0x0013, char properties: 0x14, char value handle: 0x0014, uuid: 26eb0024-b012-49a8-b1f8-394fb2032b0f
 */

object WatchDataCollector {
    private val dstSettings: ArrayList<String> = ArrayList<String>()
    private val dstWatchState: ArrayList<String> = ArrayList<String>()

    private val worldCities: HashMap<Int , CasioTimeZone.WorldCity> = HashMap<Int , CasioTimeZone.WorldCity>()
    var unmatchedCmdCount: Int = -1

    var batteryLevel: Int = 0
    var watchName: String = ""
    var moduleId:String = ""
    var homeCity: String = ""
    private var hourChime: String = ""
    var hourChime2: String = ""
    var bleFeatures: String = ""

    init {
        subscribe("CASIO_BLE_FEATURES", ::onButtonPressedInfoReceived)
        subscribe("CASIO_DST_SETTING", ::onDataReceived)
        subscribe("CASIO_WORLD_CITIES", ::onDataReceived)
        subscribe("CASIO_DST_WATCH_STATE", ::onDataReceived)
        subscribe("CASIO_WATCH_NAME", ::onDataReceived)
        subscribe("CASIO_WATCH_CONDITION", ::onDataReceived)
    }

    fun start() {
        unmatchedCmdCount = -1
        dstSettings.clear()
        dstWatchState.clear()
        worldCities.clear()

        Connection.enableNotifications()
        requestButtonPressedInformation ()
    }

    private fun requestButtonPressedInformation () {
        // CASIO_BLE_FEATURES, determine which button was pressed from these.
        writeCmd (0xC, "10")
    }

    private fun onDataReceived(data: String) {
        add(data)
        --unmatchedCmdCount
    }

    private fun onButtonPressedInfoReceived(data: String) {
        add(data)

        /* Normally for running actions we do not need full watch configuration.
        One exception is [setting time] action. This action requests
        full configuration explicitly.
         */
        if (!WatchFactory.watch.isActionButtonPressed()) {
            requestCompleteWatchSettings()
        }
    }

    fun toJson(command: String): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("WATCH_INFO_DATA", command)
        return jsonObject
    }

    private fun add(command: String) {
        val intArr = Utils.toIntArray(command)
        if (intArr.isNotEmpty() && intArr.size == 1 && intArr[0] > 0xff) {
            return
        }

        val shortStr = Utils.toCompactString(command)

        // get the first byte of the returned data, which indicates the data content.
        when (shortStr.substring(0, 2).uppercase(Locale.getDefault())) {

            // Instead of "1E", is this more readable?
            // CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.ordinal -> dstSettings.add(shortStr)
            "1E" -> dstSettings.add(shortStr)

            "1D" -> dstWatchState.add(shortStr)

            "1F" -> {
                val wc = createWordCity (shortStr)
                worldCities[wc.index] = wc // replace existing element if it exists
                if (homeCity == "") {
                    // Home city is in the fist position, so data will start with "1F 00"
                    val cityNumber = shortStr.substring(2, 4)
                    if (cityNumber.toInt() == 0) {
                        homeCity = Utils.toAsciiString(command, 2)
                    }
                }
            }
            "23" -> {
                watchName = Utils.toAsciiString(command, 1)
            }

            "28" -> {
                batteryLevel = BatteryLevelDecoder.decodeValue(command).toInt()
            }

            "10" -> {
                bleFeatures = command
                ProgressEvents.onNext(ProgressEvents.Events.ButtonPressedInfoReceived)
            }
        }
    }

    private fun createWordCity (casioString: String): CasioTimeZone.WorldCity {
        val city = Utils.toAsciiString(casioString.substring(4).trim('0'), 0)
        val index = casioString.substring(2,4).toInt()
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

                // We have collected all data from watch.
                // Send initializer data to watch, se we can set time later
                runInitCommands()
                ProgressEvents.onNext(ProgressEvents.Events.WatchInitializationCompleted)
            }
        })
    }

    fun requestCompleteWatchSettings() {

        // get DTS watch state
        writeCmdWithResponseCount(0xC, "1d00")
        writeCmdWithResponseCount(0xC, "1d02")
        writeCmdWithResponseCount(0xC, "1d04")

        // get DTS for world cities
        writeCmdWithResponseCount(0xC, "1e00")
        writeCmdWithResponseCount(0xC, "1e01")
        writeCmdWithResponseCount(0xC, "1e02")
        writeCmdWithResponseCount(0xC, "1e03")
        writeCmdWithResponseCount(0xC, "1e04")
        writeCmdWithResponseCount(0xC, "1e05")

        writeCmdWithResponseCount(0xC, "1f00")
        writeCmdWithResponseCount(0xC, "1f01")
        writeCmdWithResponseCount(0xC, "1f02")
        writeCmdWithResponseCount(0xC, "1f03")
        writeCmdWithResponseCount(0xC, "1f04")
        writeCmdWithResponseCount(0xC, "1f05")

        // watch name
        writeCmdWithResponseCount(0xC, "23")

        // battery level
        writeCmdWithResponseCount(0xC, "28")

        // app info
        // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
        // It is a hard-coded value, which is what the official app does as well.
        writeCmd(0xE, "223488F4E5D5AFC829E06D02")
    }

    private fun runInitCommands() {
        dstSettings.forEach { command ->
            writeCmdWithResponseCount(0xe, command)
        }
        dstWatchState.forEach { command ->
            writeCmdWithResponseCount(0xe, command)
        }
        worldCities.values.forEach { wc ->
            writeCmdWithResponseCount(0xe, wc.createCasioString())
        }
    }

    private fun writeCmdWithResponseCount(handle: Int, cmd: String) {
        if (unmatchedCmdCount == -1) {
            unmatchedCmdCount = 0
        }
        ++unmatchedCmdCount

        writeCmd(handle, cmd)
    }

    fun rereadHomeTimeFromWatch() {
        WatchFactory.watch.writeCmdFromString(0xC, "1f00")
    }

    fun setHomeTime(worldCityName:String) {
        WatchFactory.watch.writeCmdFromString(0xe, worldCityName)
    }

    private fun writeCmd(handle: Int, cmd: String) {
        WatchFactory.watch.writeCmdFromString(handle, cmd)
    }

    private fun isComplete(): Boolean {
        return unmatchedCmdCount == 0
    }
}