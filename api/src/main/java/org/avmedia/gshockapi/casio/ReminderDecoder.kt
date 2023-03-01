/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 10:50 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 10:50 a.m.
 */

package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.casio.ReminderMasks.Companion.ENABLED_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.FRIDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.MONDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.MONTHLY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.SATURDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.SUNDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.THURSDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.TUESDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.WEDNESDAY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.WEEKLY_MASK
import org.avmedia.gshockapi.casio.ReminderMasks.Companion.YEARLY_MASK
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.*

object ReminderDecoder {
    /* Reminders
        31 01 05 01 01 01 01 01 01 02 00 // set frequency
              00 00 00 00 00 00 00 00 00 - not set
              02 22 03 31 22 05 01 00 00
    */

    fun reminderTimeToJson(reminderStr: String): JSONObject {

        val intArr = Utils.toIntArray(reminderStr)
        if (intArr[3] == 0xFF) {
            // 0XFF indicates end of reminders
            return JSONObject("{\"end\": \"\"}")
        }

        val shortStr = Utils.toCompactString(reminderStr)
        // get the first byte of the returned data, which indicates the data content.
        val key = shortStr.substring(0, 4).uppercase(Locale.getDefault())

        val reminderAll: IntArray = Utils.toIntArray(reminderStr).toIntArray()

        // Remove the first 2 chars:
        // 0x31 05 <--- 00 23 02 21 23 02 21 00 00
        val reminder = reminderAll.sliceArray(IntRange(2, reminderAll.lastIndex))

        val reminderJson = JSONObject()

        val timePeriod = decodeTimePeriod(reminder[0])
        reminderJson.put("enabled", timePeriod.first)
        reminderJson.put("repeatPeriod", timePeriod.second)

        val timeDetailMap = decodeTimeDetail(reminder)

        reminderJson.put("startDate", timeDetailMap["startDate"])
        reminderJson.put("endDate", timeDetailMap["endDate"])
        reminderJson.put(
            "daysOfWeek",
            convertArrayListToJSONArray(timeDetailMap["daysOfWeek"] as ArrayList<String>)
        )

        return JSONObject("{\"time\": $reminderJson}")
    }

    private fun convertArrayListToJSONArray(arrayList: ArrayList<String>): JSONArray {
        val jsonArray = JSONArray()
        for (item in arrayList) {
            jsonArray.put(item)
        }
        return jsonArray
    }

    private fun decodeTimePeriod(timePeriod: Int): Pair<Boolean?, String?> {
        var enabled: Boolean = false
        var repeatPeriod: String = ""

        if (timePeriod and ENABLED_MASK == ENABLED_MASK) {
            enabled = true
        }

        repeatPeriod = when {
            timePeriod and WEEKLY_MASK == WEEKLY_MASK -> "WEEKLY"
            timePeriod and MONTHLY_MASK == MONTHLY_MASK -> "MONTHLY"
            timePeriod and YEARLY_MASK == YEARLY_MASK -> "YEARLY"
            else -> "NEVER"
        }
        return Pair(enabled, repeatPeriod)
    }

    private fun decodeTimeDetail(timeDetail: IntArray): Map<String, Any> {
        val result = HashMap<String, Any>()

        //                  00 23 02 21 23 02 21 00 00
        // start from here:    ^
        // so, skip 1
        val startDate = decodeDate(timeDetail.sliceArray(IntRange(1, timeDetail.lastIndex)))

        result["startDate"] = startDate

        //                  00 23 02 21 23 02 21 00 00
        // start from here:             ^
        // so, skip 4
        val endDate = decodeDate(timeDetail.sliceArray(IntRange(4, timeDetail.lastIndex)))

        result["endDate"] = endDate

        val dayOfWeek = timeDetail[7]
        val daysOfWeek = ArrayList<String>()
        if (dayOfWeek and SUNDAY_MASK == SUNDAY_MASK) {
            daysOfWeek.add("SUNDAY")
        }
        if (dayOfWeek and MONDAY_MASK == MONDAY_MASK) {
            daysOfWeek.add("MONDAY")
        }
        if (dayOfWeek and TUESDAY_MASK == TUESDAY_MASK) {
            daysOfWeek.add("TUESDAY")
        }
        if (dayOfWeek and WEDNESDAY_MASK == WEDNESDAY_MASK) {
            daysOfWeek.add("WEDNESDAY")
        }
        if (dayOfWeek and THURSDAY_MASK == THURSDAY_MASK) {
            daysOfWeek.add("THURSDAY")
        }
        if (dayOfWeek and FRIDAY_MASK == FRIDAY_MASK) {
            daysOfWeek.add("FRIDAY")
        }
        if (dayOfWeek and SATURDAY_MASK == SATURDAY_MASK) {
            daysOfWeek.add("SATURDAY")
        }
        result["daysOfWeek"] = daysOfWeek
        return result
    }

    private fun decodeDate(timeDetail: IntArray): JSONObject {
        // take the last 2 digits only result = {HashMap@17729}  size = 3
        val date = JSONObject()

        try {
            date.put("year", decToHex(timeDetail[0]) + 2000)
            date.put("month", intToMonthStr(decToHex(timeDetail[1])))
            date.put("day", decToHex(timeDetail[2]))
        } catch (e: NumberFormatException) {
            Timber.e("", "Could not handle time: $timeDetail")
        }

        return date
    }

    private fun decToHex(dec: Int): Int {
        return dec.toString(16).toInt()
    }

    private fun intToMonthStr(monthInt: Int): String {
        when (monthInt) {
            1 -> return "JANUARY"
            2 -> return "FEBRUARY"
            3 -> return "MARCH"
            4 -> return "APRIL"
            5 -> return "MAY"
            6 -> return "JUNE"
            7 -> return "JULY"
            8 -> return "AUGUST"
            9 -> return "SEPTEMBER"
            10 -> return "OCTOBER"
            11 -> return "NOVEMBER"
            12 -> return "DECEMBER"
        }

        return ""
    }

    fun reminderTitleToJson(titleByte: String): JSONObject {
        val intArr = Utils.toIntArray(titleByte)
        if (intArr[2] == 0xFF) {
            // 0XFF indicates end of reminders
            return JSONObject("{\"end\": \"\"}")
        }
        val reminderJson = JSONObject()
        reminderJson.put("title", Utils.toAsciiString(titleByte, 2))
        return reminderJson
    }
}