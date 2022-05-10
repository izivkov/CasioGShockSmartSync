/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.util.Preconditions.checkArgument
import com.google.gson.Gson
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
object EventsModel {

    const val MAX_REMINDERS = 5

    lateinit var events: ArrayList<Event>

    init {}

    fun init(context: Context) {
        events = CalenderEvents.getDataFromEventTable(context)
    }

    enum class RepeatPeriod(val periodDuration: String) {
        NEVER("NEVER"),
        DAILY("DAILY"),
        WEEKLY("WEEKLY"),
        MONTHLY("MONTHLY"),
        YEARLY("YEARLY")
    }

    class EventDate(var year: Int?, val month: Month?, val day: Int?) {
        fun equals(eventDate: EventDate): Boolean {
            return eventDate.year == year && eventDate.month == month && eventDate.day == day
        }
    }

    class Event(
        val title: String,
        private val startDate: EventDate?,
        var endDate: EventDate?,
        private val repeatPeriod: RepeatPeriod,
        private val daysOfWeek: ArrayList<DayOfWeek>?,
        var enabled: Boolean,
        val incompatible: Boolean,
        var selected: Boolean
    ) {
        // TODO: Validate parameters

        init {
            if (endDate == null) {
                endDate = startDate
            }
        }

        fun getPeriodFormatted(): String {
            var period = ""
            val thisYear = LocalDate.now().year

            if (startDate != null) {
                period += "${
                    capitalizeFirstAndTrim(
                        startDate.month.toString(),
                        3
                    )
                }-${startDate.day}"
                if (thisYear != startDate.year) {
                    period += ", ${startDate.year}"
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
            if (daysOfWeek != null && daysOfWeek.size > 0) {
                daysOfWeek.forEach {
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
            }
            return formattedFreq
        }

        private fun capitalizeFirstAndTrim(inStr: String, len: Int): String {
            return inStr.lowercase(Locale.getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                .substring(0, 3)
        }

        @SuppressLint("RestrictedApi")
        fun getDayOfMonthSuffix(n: Int): String? {
            checkArgument(n in 1..31, "illegal day of month: $n")
            return if (n in 11..13) {
                "th"
            } else when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
    }

    fun clear() {
        events.clear()
    }

    fun isEmpty(): Boolean {
        return events.size == 0
    }

    @Synchronized
    fun toJson(events: ArrayList<Event>): String {
        val gson = Gson()
        return gson.toJson(events)
    }

    fun getSelectedEvents(): String {
        val selectedEvents = events.filter { it.selected } as ArrayList<Event>
        return toJson(selectedEvents)
    }

    fun getSelectedCount(): Int {
        return (events.filter { it.selected } as ArrayList<Event>).size
    }

    fun createEventDate(timeMs: Long, zone: ZoneId): EventsModel.EventDate {
        val start: LocalDate =
            Instant.ofEpochMilli(timeMs).atZone(zone)
                .toLocalDate()
        return EventsModel.EventDate(
            start.year,
            start.month,
            start.dayOfMonth
        )
    }
}