/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 6:13 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 6:13 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.app.ActivityCompat
import com.philjay.Frequency
import com.philjay.RRule
import com.philjay.WeekdayNum
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Calendar

object CalenderEvents {
    private const val CALENDAR_PERMISSION_REQUEST_CODE = 3

    @SuppressLint("Range")
    fun getDataFromEventTable(context: Context): ArrayList<EventsData.Event> {
        val events: ArrayList<EventsData.Event> = ArrayList()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.READ_CALENDAR),
                CALENDAR_PERMISSION_REQUEST_CODE
            )
        }

        var cur: Cursor? = null
        val cr: ContentResolver = context.contentResolver
        val mProjection = arrayOf(
            "_id",
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALLOWED_REMINDERS,
            CalendarContract.Events.HAS_ALARM,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY,
        )

        val uri: Uri = CalendarContract.Events.CONTENT_URI

        var calendar: Calendar = Calendar.getInstance()
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.HOUR, 0)

        val selection: String = """${CalendarContract.Events.HAS_ALARM} = "1" 
            and (${CalendarContract.Events.DTSTART} >= ${calendar.timeInMillis} 
            or ${CalendarContract.Events.RRULE} IS NOT NULL)
            """

        val selectionArgs: Array<String>? = null

        cur = cr.query(uri, mProjection, selection, selectionArgs, null)
        // Event Test, 1650630600000, null, P3600S, FREQ=WEEKLY;INTERVAL=1;WKST=SU;BYDAY=TU,FR, null
        while (cur!!.moveToNext()) {
            val title: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.TITLE))
            val dateStart: String? =
                cur.getString(cur.getColumnIndex(CalendarContract.Events.DTSTART))
            val dateEnd: String? =
                cur.getString(cur.getColumnIndex(CalendarContract.Events.DTEND))
            val duration: String? =
                cur.getString(cur.getColumnIndex(CalendarContract.Events.DURATION))
            val rrule: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.RRULE))
            val rdate: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.RDATE))
            val allDay: String? =
                cur.getString(cur.getColumnIndex(CalendarContract.Events.ALL_DAY))

            Timber.i("Event: $title, allDay: $allDay")

            if (rrule != null && rrule.isNotEmpty()) {
                val rruleObj = RRule(rrule)
                val rfc5545String = rruleObj.toRFC5545String()
                Timber.i("rfc5545String $rfc5545String")
                Timber.i("RRULE ${rruleObj.freq}, ${rruleObj.interval}, ${rruleObj.byDay}")
            }

            var zone = ZoneId.systemDefault()
            if (allDay == "1") {
                zone = ZoneId.of("UTC")
            }

            val startDate = createEventDate (dateStart!!.toLong(), zone)

            var endDate = startDate
            if (dateEnd != null) {
                endDate = createEventDate(dateEnd!!.toLong(), zone)
            }

            var repeatPeriod = EventsData.RepeatPeriod.NEVER
            var daysOfWeek:ArrayList<DayOfWeek>? = null
            if (rrule != null && rrule.isNotEmpty()) {
                val rruleObj = RRule(rrule)
                repeatPeriod = toEventRepeatPeriod(rruleObj.freq)
                if (repeatPeriod == EventsData.RepeatPeriod.WEEKLY) {
                    val weekDays = rruleObj.byDay
                    daysOfWeek = createDaysOfWeek (weekDays)
                }
            }

            events.add(
                EventsData.Event(
                    title!!,
                    startDate,
                    endDate,
                    repeatPeriod,
                    daysOfWeek,
                    true
                )
            )
        }

        return events
    }

    private fun createDaysOfWeek(weekDays: ArrayList<WeekdayNum>): ArrayList<DayOfWeek>? {
        var days: ArrayList<DayOfWeek>? = ArrayList ()
        weekDays.forEach {
            when (it.weekday.name) {
                "Monday" -> days?.add(DayOfWeek.MONDAY)
                "Tuesday" -> days?.add(DayOfWeek.TUESDAY)
                "Wednesday" -> days?.add(DayOfWeek.WEDNESDAY)
                "Thursday" -> days?.add(DayOfWeek.THURSDAY)
                "Friday" -> days?.add(DayOfWeek.FRIDAY)
                "SATURDAY" -> days?.add(DayOfWeek.SATURDAY)
                else -> days?.add(DayOfWeek.SUNDAY)
            }
        }

        return days
    }

    private fun createEventDate (timeMs: Long, zone: ZoneId) : EventsData.EventDate {
        val start: LocalDate =
            Instant.ofEpochMilli(timeMs).atZone(zone)
                .toLocalDate()

        return EventsData.EventDate(
            start.year,
            start.month,
            start.dayOfMonth
        )
    }

    private fun toEventRepeatPeriod(freq: Frequency): EventsData.RepeatPeriod {
        when (freq) {
            Frequency.Monthly -> return EventsData.RepeatPeriod.MONTHLY
            Frequency.Weekly -> return EventsData.RepeatPeriod.WEEKLY
            Frequency.Yearly -> return EventsData.RepeatPeriod.YEARLY
            Frequency.Daily -> return EventsData.RepeatPeriod.DAILY
            else -> return EventsData.RepeatPeriod.NEVER
        }
    }

    private fun monthStrToCalMonth(month: Int): Month {
        when (month) {
            0 -> return Month.JANUARY
            1 -> return Month.FEBRUARY
            2 -> return Month.MARCH
            3 -> return Month.APRIL
            4 -> return Month.MAY
            5 -> return Month.JUNE
            6 -> return Month.JULY
            7 -> return Month.AUGUST
            8 -> return Month.SEPTEMBER
            9 -> return Month.OCTOBER
            10 -> return Month.NOVEMBER
            11 -> return Month.DECEMBER
            else -> return Month.JANUARY
        }
    }
}