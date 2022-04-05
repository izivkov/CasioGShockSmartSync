/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import java.time.DayOfWeek
import java.time.Month

@RequiresApi(Build.VERSION_CODES.O)
object EventsData {

    private lateinit var events: ArrayList<Event>

    init {}

    enum class RepeatPeriod(val periodDuration: String) {
        NEVER("NEVER"),
        DAILY("DAILY"),
        WEEKLY("WEEKLY"),
        MONTHLY("MONTHLY"),
        YEARLY("YEARLY")
    }

    class EventDate(var year: Int?, val month: Month?, val day: Int?) {
        // TODO: Validate parameters
    }

    class Event(
        val title: String,
        private val startDate: EventDate?,
        private var endDate: EventDate?,
        val repeatPeriod: RepeatPeriod,
        val daysOfWeek: ArrayList<DayOfWeek>?,
        var enabled:Boolean = true
    ) {
        init {
            if (endDate == null) {
                endDate = startDate
            }
        }
        // TODO: Validate parameters
    }

    @Synchronized
    fun toJson(events: ArrayList<Event>): String {
        val gson = Gson()
        return gson.toJson(events)
    }

    fun getEvents (context: Context): String {
        if (!this::events.isInitialized) {
            events = CalenderEvents.getDataFromEventTable(context)
        }
        return toJson(events)
    }

    private fun test() {
        // make up some test data
        events.add(
            Event(
                "Once only",
                EventDate(2022, Month.APRIL, 23),
                null,
                RepeatPeriod.NEVER,
                null
            )
        )
        events.add(
            Event(
                "Mon, Thr",
                null,
                null,
                RepeatPeriod.WEEKLY,
                arrayListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
            )
        )

        events.add(
            Event(
                "Period, 22/06/23->23/07/2",
                EventDate(2022, Month.JUNE, 23),
                EventDate(2023, Month.JULY, 2),
                RepeatPeriod.NEVER,
                null,
                false
            )
        )

        events.add(
            Event(
                "once per year",
                EventDate(2022, Month.JUNE, 2),
                null,
                RepeatPeriod.YEARLY,
                null
            )
        )

        events.add(
            Event(
                "Monthly",
                EventDate(2022, Month.APRIL, 15),
                null,
                RepeatPeriod.MONTHLY,
                null
            )
        )
    }
}