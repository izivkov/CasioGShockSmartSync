package org.avmedia.gshockapi.io

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.CasioTimeZone
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.reflect.KSuspendFunction1

object TimeIO {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun set(timeZone: String?) {
        if (WatchInfo.model == WatchInfo.WATCH_MODEL.B2100) {
            initializeForSettingTimeForB2100()
        } else {
            initializeForSettingTimeForB5600()
        }

        if (timeZone != null && TimeZone.getAvailableIDs().contains(timeZone)) {
            // Update the HomeTime according to the current TimeZone
            val city = CasioTimeZone.TimeZoneHelper.parseCity(timeZone)
            if (city != null) {
                val homeTime = HomeTimeIO.request()
                if (homeTime.uppercase() != city.uppercase()) {
                    HomeTimeIO.set(timeZone)
                }
            }
        }

        Connection.sendMessage(
            "{action: \"SET_TIME\", value: ${
                Clock.systemDefaultZone().millis()
            }}"
        )
    }

    private suspend fun getWorldCities(cityNum: Int): String {
        return WorldCitiesIO.request(cityNum)
    }

    private suspend fun getDSTWatchState(state: CasioIO.DTS_STATE): String {
        return DstWatchStateIO.request(state)
    }

    private suspend fun getDSTForWorldCities(cityNum: Int): String {
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

        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.ZERO)
        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.TWO)
        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.FOUR)

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

        readAndWrite(::getDSTWatchState, CasioIO.DTS_STATE.ZERO)

        readAndWrite(::getDSTForWorldCities, 0)
        readAndWrite(::getDSTForWorldCities, 1)

        readAndWrite(::getWorldCities, 0)
        readAndWrite(::getWorldCities, 1)
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
        @SuppressLint("NewApi")
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