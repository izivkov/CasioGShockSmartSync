package org.avmedia.gshockapi

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.util.Preconditions
import org.avmedia.gshockapi.utils.Utils.getBooleanSafe
import org.avmedia.gshockapi.utils.Utils.getStringSafe
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale

data class Event(
    val title: String,
    val startDate: EventDate?,
    val endDate: EventDate?,
    val repeatPeriod: RepeatPeriod,
    val daysOfWeek: List<DayOfWeek>?,
    val enabled: Boolean,
    val incompatible: Boolean
) {
    constructor(json: JSONObject) : this(
        title = json.getString("title"),
        startDate = json.getJSONObject("time").getJSONObject("startDate").let { date ->
            EventDate(
                date.getInt("year"),
                parseMonth(date.getString("month")),
                date.getInt("day")
            )
        },
        endDate = json.getJSONObject("time").getJSONObject("endDate").let { date ->
            EventDate(
                date.getInt("year"),
                parseMonth(date.getString("month")),
                date.getInt("day")
            )
        },
        repeatPeriod = parseRepeatPeriod(json.getJSONObject("time").getStringSafe("repeatPeriod") as String),
        daysOfWeek = parseWeekDays(json.getJSONObject("time").getJSONArray("daysOfWeek")),
        enabled = json.getJSONObject("time").getBooleanSafe("enabled") ?: false,
        incompatible = json.getJSONObject("time").getBooleanSafe("incompatible") ?: false
    )

    companion object {
        private fun parseWeekDays(jsonArray: JSONArray): List<DayOfWeek> =
            (0 until jsonArray.length()).map { i ->
                DayOfWeek.valueOf((jsonArray[i] as String).uppercase())
            }

        private fun parseMonth(monthStr: String): Month =
            Month.valueOf(monthStr.uppercase())

        private fun parseRepeatPeriod(repeatPeriodStr: String): RepeatPeriod =
            RepeatPeriod.valueOf(repeatPeriodStr.uppercase())

        private fun formatDayOfMonth(n: Int): String {
            return n.toString() + when {
                n in 11..13 -> "th"
                n % 10 == 1 -> "st"
                n % 10 == 2 -> "nd"
                n % 10 == 3 -> "rd"
                else -> "th"
            }
        }

        private fun capitalizeFirstAndTrim(inStr: String, len: Int): String =
            inStr.lowercase(Locale.getDefault())
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                .take(len)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getPeriodFormatted(): String = buildString {
        startDate?.let { start ->
            val thisYear = LocalDate.now().year
            append("${capitalizeFirstAndTrim(start.month.toString(), 3)}-${start.day}")
            if (thisYear != start.year) append(", ${start.year}")

            if (endDate != null && endDate != startDate) {
                append(" to ${capitalizeFirstAndTrim(endDate.month.toString(), 3)}-${endDate.day}")
                if (thisYear != endDate.year) append(", ${endDate.year}")
            }
        }
    }

    fun getFrequencyFormatted(): String = when (repeatPeriod) {
        RepeatPeriod.WEEKLY -> daysOfWeek?.joinToString(",") {
            capitalizeFirstAndTrim(it.name, 3)
        }.orEmpty()
        RepeatPeriod.YEARLY -> startDate?.let {
            "${capitalizeFirstAndTrim(it.month.toString(), 3)}-${it.day}${formatDayOfMonth(it.day)} each year"
        }.orEmpty()
        RepeatPeriod.MONTHLY -> startDate?.let {
            "${it.day}${formatDayOfMonth(it.day)} each month"
        }.orEmpty()
        RepeatPeriod.NEVER -> ""
        else -> ""
    }
}
