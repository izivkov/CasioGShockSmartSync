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
    private val dstSettings: ArrayList<String> = ArrayList<String>()
    private val dstWatchState: ArrayList<String> = ArrayList<String>()

    private val worldCities: HashMap<Int, CasioTimeZone.WorldCity> = HashMap<Int, CasioTimeZone.WorldCity>()
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

            // DST settings, i.e. 1e 04 7a 00 20 04 00
            "1E" -> dstSettings.add(shortStr)

            // watch state, i.e. 1d0001030202763a01ffffffffffff
            "1D" -> dstWatchState.add(shortStr)

            // World cities, i.e. 1f00544f524f4e544f00000000000000000000000000
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

            // watch name, i.e. 23434153494f2047572d42353630300000000000
            "23" -> {
                watchName = Utils.toAsciiString(command, 1)
            }

            // battery level, i.e. 28132400
            "28" -> {
                batteryLevel = BatteryLevelDecoder.decodeValue(command).toInt()
            }

            // Ble features. We can detect from these which button was pressed.
            // i.e. 101762532585fd7f00030fffffffff24000000
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

        // World cities
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

    // we have collected all data from watch. Write it back.
    // This is needed in order to be able to set time on watch.
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