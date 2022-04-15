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
import timber.log.Timber
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
        subscribe("WATCH_INFO_DATA", ::onDataReceived)
    }

    private fun onDataReceived(data: String) {
        add(data)
        --unmatchedCmdCount
    }

    fun add(command: String) {
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
            "23" -> watchName = Utils.toAsciiString(command, 1)
            "28" -> {
                batteryLevel = 0
                var cmdInts = Utils.toIntArray(command)
                // command looks like 0x28 13 1E 00.
                // 50% level is obtain from the second Int 13:
                // 0x13 = 0b00010011
                // take MSB 0b0001. If it is not 0, we have 50% charge
                val MASK_50_PERCENT = 0b00010000
                batteryLevel += if (cmdInts[1] or MASK_50_PERCENT != 0) 50 else 0

                // Fine value is obtained from the 3rd integer, 0x1E. The LSB (0xE) represents
                // the fine value between 0 and 0xf, which is the other 50%. So, to
                // get this value, we have 50% * 0xe / 0xf. We add this to the previous battery level.
                // So, for command 0x28 13 1E 00, our battery level would be:
                // 50% (from 0x13) + 47 = 97%
                // The 47 number was obtained from 50 * 0xe / 0xf or 50 * 14/15 = 46.66

                val MASK_FINE_VALUE = 0xf
                val fineValue = cmdInts[2] and MASK_FINE_VALUE
                batteryLevel += 50 * fineValue / 15
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
                ProgressEvents.onNext(ProgressEvents.Events.PhoneDataCollected)
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