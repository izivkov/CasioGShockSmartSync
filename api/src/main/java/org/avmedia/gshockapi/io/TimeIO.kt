package org.avmedia.gshockapi.io

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import org.avmedia.gshockapi.io.DstWatchStateIO.DTS_VALUE.*
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import java.time.*
import java.util.*
import kotlin.reflect.KSuspendFunction1

@RequiresApi(Build.VERSION_CODES.O)
object TimeIO {
    init {}

    private var timeZone: String = TimeZone.getDefault().id
    private var casioTimezone = CasioTimeZoneHelper.findTimeZone(timeZone)

    fun setTimezone(timeZone:String) {
        this.timeZone = timeZone
        casioTimezone = CasioTimeZoneHelper.findTimeZone(timeZone)
    }

    suspend fun set() {
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

    private suspend fun getDSTWatchState(state: CasioIO.DTS_STATE): String {
        return DstWatchStateIO.request(state)
    }

    private suspend fun getDSTWatchStateWithTZ(state: CasioIO.DTS_STATE): String {
        val origDTS = getDSTWatchState(state)
        val hasDST =
            if (casioTimezone.dstOffset > 0) ON_AND_AUTO else OFF
        return DstWatchStateIO.setDST(origDTS, hasDST)
    }

    private suspend fun getDSTForWorldCities(cityNum: Int): String {
        return DstForWorldCitiesIO.request(cityNum)
    }

    private suspend fun getDSTForWorldCitiesWithTZ(cityNum: Int): String {
        var origDSTForCity = getDSTForWorldCities(cityNum)
        return DstForWorldCitiesIO.setDST(origDSTForCity, casioTimezone)
    }

    private suspend fun getWorldCities(cityNum: Int): String {
        return WorldCitiesIO.request(cityNum)
    }
    private suspend fun getWorldCitiesWithTZ(cityNum: Int): String {
        val newCity = WorldCitiesIO.parseCity(timeZone)
        val encoded = WorldCitiesIO.encodeAndPad(newCity!!, cityNum)
        CasioIO.removeFromCache(encoded)
        return encoded
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

        readAndWrite(::getDSTWatchStateWithTZ, CasioIO.DTS_STATE.ZERO)
        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.TWO)
        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.FOUR)

        readAndWrite(::getDSTForWorldCitiesWithTZ, 0)
        readAndWrite(::getDSTForWorldCities, 1)
        readAndWrite(::getDSTForWorldCities, 2)
        readAndWrite(::getDSTForWorldCities, 3)
        readAndWrite(::getDSTForWorldCities, 4)
        readAndWrite(::getDSTForWorldCities, 5)

        readAndWrite(::getWorldCitiesWithTZ, 0)
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

        readAndWrite(::getDSTWatchStateWithTZ, CasioIO.DTS_STATE.ZERO)

        readAndWrite(::getDSTForWorldCitiesWithTZ, 0)
        readAndWrite(::getDSTForWorldCities, 1)

        readAndWrite(::getWorldCitiesWithTZ, 0)
        readAndWrite(::getWorldCities, 1)
    }

    fun sendToWatchSet(message: String) {
        val dateTimeMs: Long = JSONObject(message).get("value") as Long

        val dateTime =
            Instant.ofEpochMilli(dateTimeMs).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val timeData = TimeEncoder.prepareCurrentTime(dateTime)
        var timeCommand =
            Utils.byteArrayOfInts(CasioConstants.CHARACTERISTICS.CASIO_CURRENT_TIME.code) + timeData

        CasioIO.writeCmd(0x000e, timeCommand)
    }

    object TimeEncoder {
        fun prepareCurrentTime(date: LocalDateTime): ByteArray {
            val arr = ByteArray(10)
            val year = date.year
            arr[0] = (year ushr 0 and 0xff).toByte()
            arr[1] = (year ushr 8 and 0xff).toByte()
            arr[2] = date.month.value.toByte()
            arr[3] = date.dayOfMonth.toByte()
            arr[4] = date.hour.toByte()
            arr[5] = date.minute.toByte()
            arr[6] = date.second.toByte()
            arr[7] = date.dayOfWeek.value.toByte()
            arr[8] = (date.nano / 1000000).toByte()
            arr[9] = 1 // or 0?
            return arr
        }
    }
}