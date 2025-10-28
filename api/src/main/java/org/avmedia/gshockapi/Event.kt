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
import java.util.*

data class Event(
    var title: String,
    private var startDate: EventDate?,
    var endDate: EventDate?,
    private var repeatPeriod: RepeatPeriod,
    private var daysOfWeek: List<DayOfWeek>?,
    var enabled: Boolean,
    var incompatible: Boolean,
) {
    init {
        if (endDate == null) {
            endDate = startDate
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    constructor(json: JSONObject) : this(
        "", null, null, RepeatPeriod.NEVER, null, false, false
    ) {
        val event = createEvent(json)

        this.title = event.title
        this.startDate = event.startDate
        this.endDate = event.endDate
        this.repeatPeriod = event.repeatPeriod
        this.daysOfWeek = event.daysOfWeek
        this.enabled = event.enabled
        this.incompatible = event.incompatible
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createEvent(eventJsn: JSONObject): Event {

        @RequiresApi(Build.VERSION_CODES.O)
        fun getArrayListFromJSONArray(jsonArray: JSONArray): ArrayList<DayOfWeek> {
            val list = ArrayList<DayOfWeek>()

            fun stringToDayOfWeek(dayStr: String): DayOfWeek {
                return when (dayStr.uppercase()) {
                    "MONDAY" -> DayOfWeek.MONDAY
                    "TUESDAY" -> DayOfWeek.TUESDAY
                    "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                    "THURSDAY" -> DayOfWeek.THURSDAY
                    "FRIDAY" -> DayOfWeek.FRIDAY
                    "SATURDAY" -> DayOfWeek.SATURDAY
                    "SUNDAY" -> DayOfWeek.SUNDAY
                    else -> DayOfWeek.MONDAY
                }
            }

            for (i in 0 until jsonArray.length()) {
                val dayStr: String = jsonArray[i] as String
                val dayOfWeek: DayOfWeek = stringToDayOfWeek(dayStr)
                list.add(dayOfWeek)
            }
            return list
        }

        fun stringToMonth(monthStr: String): Month {
            return when (monthStr.lowercase()) {
                "january", "jan" -> Month.JANUARY
                "february", "feb" -> Month.FEBRUARY
                "march", "mar" -> Month.MARCH
                "april", "apr" -> Month.APRIL
                "may" -> Month.MAY
                "june", "jun" -> Month.JUNE
                "july", "jul" -> Month.JULY
                "august", "aug" -> Month.AUGUST
                "september", "sep", "sept" -> Month.SEPTEMBER
                "october", "oct" -> Month.OCTOBER
                "november", "nov" -> Month.NOVEMBER
                "december", "dec" -> Month.DECEMBER
                else -> Month.JANUARY
            }
        }

        fun stringToRepeatPeriod(repeatPeriodStr: String): RepeatPeriod =
            when (repeatPeriodStr.lowercase()) {
                "daily" -> RepeatPeriod.DAILY
                "weekly" -> RepeatPeriod.WEEKLY
                "monthly" -> RepeatPeriod.MONTHLY
                "yearly" -> RepeatPeriod.YEARLY
                "never" -> RepeatPeriod.NEVER
                else -> throw IllegalArgumentException("Invalid Repeat Period")
            }

        val timeObj = eventJsn.get("time") as JSONObject
        val title = eventJsn.get("title") as String

        val startDate = timeObj.get("startDate") as JSONObject
        val endDate = timeObj.get("endDate") as JSONObject
        val weekDays = timeObj.getJSONArray("daysOfWeek")
        val enabled = timeObj.getBooleanSafe("enabled") ?: false
        val incompatible = timeObj.getBooleanSafe("incompatible") ?: false
        val repeatPeriod = stringToRepeatPeriod(timeObj.getStringSafe("repeatPeriod") ?: "never")

        return Event(
            title,
            EventDate(
                startDate.getInt("year"),
                stringToMonth(startDate.getString("month")),
                startDate.getInt("day")
            ),
            EventDate(
                endDate.getInt("year"),
                stringToMonth(endDate.getString("month")),
                endDate.getInt("day")
            ),
            repeatPeriod,
            getArrayListFromJSONArray(weekDays),
            enabled,
            incompatible,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getPeriodFormatted(): String {
        var period = ""
        val thisYear = LocalDate.now().year

        if (startDate != null) {
            period += "${
                capitalizeFirstAndTrim(
                    startDate!!.month.toString(),
                    3
                )
            }-${startDate!!.day}"
            if (thisYear != startDate!!.year) {
                period += ", ${startDate!!.year}"
            }
        }
        if (endDate != null && !startDate!!.equals(endDate!!)) {
            period += " to ${
                capitalizeFirstAndTrim(
                    endDate!!.month.toString(),
                    3
                )
            }-${endDate!!.day}"
            if (thisYear != endDate!!.year) {
                period += ", ${endDate!!.year}"
            }
        }
        return period
    }

    private fun getDaysOfWeekFormatted(): String {
        var daysOfWeekStr = ""
        if (daysOfWeek != null && daysOfWeek!!.size > 0) {
            daysOfWeek!!.forEach {
                daysOfWeekStr += "${capitalizeFirstAndTrim(it.name, 3)},"
            }
        } else {
            return ""
        }

        return daysOfWeekStr.dropLast(1)
    }

    fun getFrequencyFormatted(): String {
        var formattedFreq = ""
        when (repeatPeriod) {
            RepeatPeriod.WEEKLY -> {
                formattedFreq = getDaysOfWeekFormatted()
            }

            RepeatPeriod.YEARLY -> {
                formattedFreq = "${
                    capitalizeFirstAndTrim(
                        startDate?.month.toString(),
                        3
                    )
                }-${startDate?.day}${getDayOfMonthSuffix(startDate?.day!!.toInt())} each year"
            }

            RepeatPeriod.MONTHLY -> {
                formattedFreq =
                    "${startDate?.day}${getDayOfMonthSuffix(startDate?.day!!.toInt())} each month"
            }

            RepeatPeriod.NEVER -> {
                Timber.i("Single-time event...")
            }

            else -> {
                Timber.i("Invalid frequency format")
            }
        }
        return formattedFreq
    }

    private fun capitalizeFirstAndTrim(inStr: String, len: Int): String {
        return inStr.lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .substring(0, len)
    }

    @SuppressLint("RestrictedApi")
    fun getDayOfMonthSuffix(n: Int): String {
        Preconditions.checkArgument(n in 1..31, "illegal day of month: $n")
        return if (n in 11..13) {
            "th"
        } else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    override fun toString(): String {
        return "Event(title='$title', startDate=$startDate, endDate=$endDate, repeatPeriod=$repeatPeriod, daysOfWeek=$daysOfWeek, enabled=$enabled, incompatible=$incompatible)"
    }
}