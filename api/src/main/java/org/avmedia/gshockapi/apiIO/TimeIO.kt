package org.avmedia.gshockapi.apiIO

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.TimeEncoder
import org.avmedia.gshockapi.casio.WatchFactory
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.reflect.KSuspendFunction1

object TimeIO {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun set () {
        if (WatchInfo.model == WatchInfo.WATCH_MODEL.B2100) {
            initializeForSettingTimeForB2100()
        } else {
            initializeForSettingTimeForB5600()
        }

        Connection.sendMessage(
            "{action: \"SET_TIME\", value: ${
                Clock.systemDefaultZone().millis()
            }}"
        )
    }

    private suspend fun getWorldCities(cityNum: Int):String {
        return WorldCitiesIO.request(cityNum)
    }

    private suspend fun getDSTWatchState(state: BluetoothWatch.DTS_STATE):String {
        return DstWatchStateIO.request(state)
    }

    private suspend fun
            getDSTForWorldCities(cityNum: Int):String {
        return DstForWorldCitiesIO.request(cityNum)
    }

    /**
     * This function is internally called by [setTime] to initialize some values.
     */
    private suspend fun initializeForSettingTimeForB5600() {
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

    private suspend fun initializeForSettingTimeForB2100() {
        // Before we can set time, we must read and write back these values.
        // Why? Not sure, ask Casio

        suspend fun <T> readAndWrite(function: KSuspendFunction1<T, String>, param: T) {
            val ret: String = function(param)
            val shortStr = Utils.toCompactString(ret)
            CasioIO.writeCmd(0xE, shortStr)
        }

        readAndWrite(::getDSTWatchState, BluetoothWatch.DTS_STATE.ZERO)

        readAndWrite(::getDSTForWorldCities, 0)
        readAndWrite(::getDSTForWorldCities, 1)

        readAndWrite(::getWorldCities, 0)
        readAndWrite(::getWorldCities, 1)
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun sendToWatchSet(message:String) {
        val dateTimeMs: Long = JSONObject(message).get("value") as Long

        val dateTime = Instant.ofEpochMilli(dateTimeMs).atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val timeData = TimeEncoder.prepareCurrentTime(dateTime)
        var timeCommand =
            Utils.byteArrayOfInts(CasioConstants.CHARACTERISTICS.CASIO_CURRENT_TIME.code) + timeData

        WatchFactory.watch.writeCmd(0x000e, timeCommand)
    }
}