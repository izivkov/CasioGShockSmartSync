/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 10:50 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 10:50 a.m.
 */

package org.avmedia.gShockPhoneSync.casioB5600

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.Utils.getBooleanSafe
import org.avmedia.gShockPhoneSync.utils.Utils.getJSONArraySafe
import org.avmedia.gShockPhoneSync.utils.Utils.getJSONObjectSafe
import org.avmedia.gShockPhoneSync.utils.Utils.getStringSafe
import org.json.JSONArray
import org.json.JSONObject

object ReminderEncoder {
    /* Reminders
        30 01 50 61 79 20 52 68 6f 64 6f 72 61 2e 00 00 00 00 00 00 // Title, Pay Rhodora
        31 01 05 01 01 01 01 01 01 02 00 // set frequency
        00 00 00 00 00 00 00 00 00 - not set
        02 22 03 31 22 05 01 00 00
    */

    val YEARLY_MASK =       0b00001000
    val NONTHLY_MASK =      0b00010000
    val WEEKLY_MASK =       0b00000100

    val SUNDAY_MASK =       0b00000001
    val MONDAY_MASK =       0b00000010
    val TUESDAY_MASK =      0b00000100
    val WEDNESSDAY_MASK =   0b00001000
    val THURSDAY_MASK =     0b00010000
    val FRIDAY_MASK =       0b00100000
    val SATURDAY_MASK =     0b01000000

    val ENABLED_MASK =      0b00000001

    @RequiresApi(Build.VERSION_CODES.O)
    fun reminderTimeFromJson(reminderJson: JSONObject): IntArray {

        val enabled = reminderJson.getBooleanSafe("enabled")
        val repeatPeriod = reminderJson.getStringSafe("repeatPeriod")
        val startDate = reminderJson.getJSONObjectSafe("startDate")
        val endDate = reminderJson.getJSONObjectSafe("endDate")
        val daysOfWeek = reminderJson.getJSONArraySafe("daysOfWeek")

        var reminderCmd: IntArray = IntArray(0)

        reminderCmd += createTimePeriod(enabled, repeatPeriod)
        reminderCmd += createTimeDetail(repeatPeriod, startDate, endDate, daysOfWeek)

        return reminderCmd
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createTimeDetail(
        repeatPeriod: String?,
        startDate: JSONObject?,
        endDate: JSONObject?,
        daysOfWeek: JSONArray?
    ): IntArray {
        var timeDetail = IntArray(8)

        when (repeatPeriod) {
            "NEVER" -> {
                // Once only, Jun 3
                // 31 01 01 22 06 03 22 06 03 00 00
                // 31 04 09 22 06 02 22 06 02 00 00

                encodeDate (timeDetail, startDate, endDate)
            }
            "WEEKLY" -> {
                /*
            05 01 01 01 01 01 01 20 00 - every friday
            05 01 01 01 01 01 01 19 00
            05 01 01 01 01 01 01 02 00 - evey monday
            00011001 - Sun, Wed, Thu
            */
                timeDetail[0] = 1
                timeDetail[1] = 1
                timeDetail[2] = 1
                timeDetail[3] = 1
                timeDetail[4] = 1
                timeDetail[5] = 1

                var dayOfWeek = 0

                if (daysOfWeek != null) {
                    (0 until daysOfWeek.length()).forEach { i ->
                        when (daysOfWeek.getString(i)) {
                            "SUNDAY" -> dayOfWeek = dayOfWeek or SUNDAY_MASK
                            "MONDAY" -> dayOfWeek = dayOfWeek or MONDAY_MASK
                            "TUESDAY" -> dayOfWeek = dayOfWeek or TUESDAY_MASK
                            "WEDNESDAY" -> dayOfWeek = dayOfWeek or WEDNESSDAY_MASK
                            "THURSDAY" -> dayOfWeek = dayOfWeek or THURSDAY_MASK
                            "FRIDAY" -> dayOfWeek = dayOfWeek or FRIDAY_MASK
                            "SATURDAY" -> dayOfWeek = dayOfWeek or SATURDAY_MASK
                        }
                    }
                }
                timeDetail[6] = dayOfWeek
                timeDetail[7] = 0
            }
            "MONTHLY" -> {
                // Monthly, may 3
                // 31 05 11 22 05 03 22 05 03 00 00
                encodeDate (timeDetail, startDate, endDate)
            }
            "YEARLY" -> {
                /*
            09 22 05 02 22 05 02 00 00 - once every year May 2
            03 22 03 31 22 05 01 00 00 - Period: mar 21 to may 1
            02 22 03 31 22 05 01 00 00 - same but disabled
            01 22 04 01 22 04 01 00 00 - only once, apr 1
            01 22 04 18 22 04 18 00 00 - once only, apr 18
             */
                encodeDate (timeDetail, startDate, endDate)
            }
        }
        return timeDetail
    }

    private fun encodeDate (timeDetail: IntArray, startDate: JSONObject?, endDate: JSONObject?) {
        // take the last 2 digits only
        timeDetail[0] = encodeDateField(startDate!!.getInt("year").rem(2000))
        timeDetail[1] = encodeDateField(monthStrToInt(startDate.getString("month")))
        timeDetail[2] = encodeDateField(startDate.getInt("day"))

        timeDetail[3] = encodeDateField(endDate!!.getInt("year").rem(2000))
        timeDetail[4] = encodeDateField(monthStrToInt(endDate.getString("month")))
        timeDetail[5] = encodeDateField(endDate.getInt("day"))

        timeDetail[6] = 0
        timeDetail[7] = 0
    }
    private fun encodeDateField(dateField: Int): Int {
        // Values are stored in hex numbers, that look like decimals, i.e.
        // 22 04 18 represents 2022, Apr 18. So, we must convert the decimal
        // numbers from the Json to hex (i.e 0x22 0x04 0x18).
        return Integer.parseInt(dateField.toString(), 16)
    }

    private fun monthStrToInt(monthStr: String): Int {
        when (monthStr) {
            "JANUARY" -> return 1
            "FEBRUARY" -> return 2
            "MARCH" -> return 3
            "APRIL" -> return 4
            "MAY" -> return 5
            "JUNE" -> return 6
            "JULY" -> return 7
            "AUGUST" -> return 8
            "SEPTEMBER" -> return 9
            "OCTOBER" -> return 10
            "NOVEMBER" -> return 11
            "DECEMBER" -> return 12
        }

        return -1
    }

    private fun createTimePeriod(enabled: Boolean?, repeatPeriod: String?): Int {
        var timePeriod: Int = 0

        if (enabled == true) {
            timePeriod = timePeriod or ENABLED_MASK
        }
        when (repeatPeriod) {
            "WEEKLY" -> timePeriod = timePeriod or WEEKLY_MASK
            "MONTHLY" -> timePeriod = timePeriod or NONTHLY_MASK
            "YEARLY" -> timePeriod = timePeriod or YEARLY_MASK
        }
        return timePeriod
    }

    fun reminderTitleFromJson(reminderJson: JSONObject): ByteArray {
        var titleStr: String = reminderJson.get("title") as String
        return Utils.toByteArray(titleStr, 18)
    }
}