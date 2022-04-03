/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import java.time.DayOfWeek
import java.time.Month

@RequiresApi(Build.VERSION_CODES.O)
object RemindersData {

    private val reminders = ArrayList<Reminder>()

    init {
        // make up some test data
        reminders.add(
            Reminder(
                "Once only",
                ReminderDate(2022, Month.APRIL, 23),
                null,
                RepeatPeriod.NEVER,
                null
            )
        )
        reminders.add(
            Reminder(
                "Mon, Thr",
                null,
                null,
                RepeatPeriod.WEEKLY,
                arrayListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
            )
        )

        reminders.add(
            Reminder(
                "Period, 22/06/23->23/07/2",
                ReminderDate(2022, Month.JUNE, 23),
                ReminderDate(2023, Month.JULY, 2),
                RepeatPeriod.NEVER,
                null,
                false
            )
        )

        reminders.add(
            Reminder(
                "once per year",
                ReminderDate(2022, Month.JUNE, 2),
                null,
                RepeatPeriod.YEARLY,
                null
            )
        )

        reminders.add(
            Reminder(
                "Monthly",
                ReminderDate(2022, Month.APRIL, 15),
                null,
                RepeatPeriod.MONTHLY,
                null
            )
        )
    }

    enum class RepeatPeriod(val periodDuration: String) {
        NEVER("NEVER"),
        WEEKLY("WEEKLY"),
        MONTHLY("MONTHLY"),
        YEARLY("YEARLY")
    }

    class ReminderDate(var year: Int?, val month: Month?, val day: Int?) {
        // TODO: Validate parameters
    }

    class Reminder(
        val title: String,
        private val startDate: ReminderDate?,
        private var endDate: ReminderDate?,
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

    fun clear() {
        reminders.clear()
    }

    fun isEmpty(): Boolean {
        return reminders.size == 0
    }

    @Synchronized
    fun fromJson(jsonStr: String) {
        val gson = Gson()
        val reminderArr = gson.fromJson(jsonStr, Array<Reminder>::class.java)
        reminders.addAll(reminderArr)
    }

    @Synchronized
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(reminders)
    }
}