/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gShockPhoneSync.casioB5600

import android.annotation.SuppressLint
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.json.JSONObject
import java.util.Locale

object WatchDataCollector {
    private val dstSettings: ArrayList<String> = ArrayList<String>()
    private val dstWatchState: ArrayList<String> = ArrayList<String>()
    private val worldCities: ArrayList<String> = ArrayList<String>()
    var unmatchedCmdCount: Int = -1

    var batteryLevel: Int = 0
    var watchName: String = ""
    var homeCity: String = ""
    private var hourChime: String = ""
    var hourChime2: String = ""

    init {
        subscribe("CASIO_DST_SETTING", ::onDataReceived)
        subscribe("CASIO_WORLD_CITIES", ::onDataReceived)
        subscribe("CASIO_DST_WATCH_STATE", ::onDataReceived)
        subscribe("CASIO_WATCH_NAME", ::onDataReceived)
        subscribe("CASIO_WATCH_CONDITION", ::onDataReceived)
    }

    private fun onDataReceived(data: String) {
        add(data)
        --unmatchedCmdCount
    }

    fun toJson(command: String): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("WATCH_INFO_DATA", command)
        return jsonObject
    }

    private fun
        add(command: String) {
        val shortStr = Utils.toCompactString(command)
        when (shortStr.substring(0, 2).uppercase(Locale.getDefault())) {
            "1E" -> dstSettings.add(shortStr)
            "1D" -> dstWatchState.add(shortStr)
            "1F" -> {
                worldCities.add(shortStr)
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
                batteryLevel = BatteryLevelDecoder.decodeValue (command).toInt()
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        WatchDataEvents.addSubject(subject)

        // receive values from the commands we issued in start()
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject, onNext = {
            onDataReceived(it as String)

            if (isComplete()) {
                ProgressEvents.onNext(ProgressEvents.Events.WatchDataCollected)
            }
        })
    }

    fun start() {
        unmatchedCmdCount = -1
        dstSettings.clear()
        dstWatchState.clear()
        worldCities.clear()

        Connection.enableNotifications()

        // get DTS watch state
        writeCmd(0xC, "1d00")
        writeCmd(0xC, "1d02")
        writeCmd(0xC, "1d04")

        // get DTS for world cities
        writeCmd(0xC, "1e00")
        writeCmd(0xC, "1e01")
        writeCmd(0xC, "1e02")
        writeCmd(0xC, "1e03")
        writeCmd(0xC, "1e04")
        writeCmd(0xC, "1e05")

        // get world cities
        writeCmd(0xC, "1f00")
        writeCmd(0xC, "1f01")
        writeCmd(0xC, "1f02")
        writeCmd(0xC, "1f03")
        writeCmd(0xC, "1f04")
        writeCmd(0xC, "1f05")

        // watch name
        writeCmd(0xC, "23")

        // battery level
        writeCmd(0xC, "28")
    }

    fun runInitCommands() {
        dstSettings.forEach { command ->
            writeCmd(0xe, command)
        }
        dstWatchState.forEach { command ->
            writeCmd(0xe, command)
        }
        worldCities.forEach { command ->
            writeCmd(0xe, command)
        }
    }

    private fun writeCmd(handle: Int, cmd: String) {
        if (unmatchedCmdCount == -1) {
            unmatchedCmdCount = 0
        }
        ++unmatchedCmdCount
        CasioSupport.writeCmdFromString(handle, cmd)
    }

    private fun isComplete(): Boolean {
        return unmatchedCmdCount == 0
    }
}