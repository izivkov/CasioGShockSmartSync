/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 6:13 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 6:13 p.m.
 */

package org.avmedia.gshockapi

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.ProgressEvents
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object CalenderEvents {
    @RequiresApi(Build.VERSION_CODES.O)
    fun getDataFromEventTable(context: Context): ArrayList<EventsModel.Event> {
        return getEvents(context)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("Range")
    private fun getEvents(context: Context): ArrayList<EventsModel.Event> {
        val events: ArrayList<EventsModel.Event> = ArrayList()
        val cur: Cursor?
        val cr: ContentResolver = context.contentResolver

        val mProjection = arrayOf(
            "_id",
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.HAS_ALARM,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.ALL_DAY,
        )

        val calendar: Calendar = Calendar.getInstance()
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.HOUR, 0)

        val selection =
            """
            ${CalendarContract.Events.HAS_ALARM} = "1"
            and (${CalendarContract.Events.DTEND} >= ${calendar.timeInMillis}
            or ${CalendarContract.Events.RRULE} IS NOT NULL)
            """.trimIndent()

        val selectionArgs: Array<String>? = null

        val uri: Uri = CalendarContract.Events.CONTENT_URI
        cur = cr.query(
            uri,
            mProjection,
            selection,
            selectionArgs,
            null)

        CalendarObserver.register(cr, uri)

        while (cur!!.moveToNext()) {
            var title: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.TITLE))
            title = if (title.isNullOrBlank()) "(No title)" else title

            val dateStart: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.DTSTART))
            val rrule: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.RRULE))
            val allDay: String? = cur.getString(cur.getColumnIndex(CalendarContract.Events.ALL_DAY))

            var zone = ZoneId.systemDefault()
            if (allDay == "1") {
                zone = ZoneId.of("UTC")
            }

            val startDate = EventsModel.createEventDate(dateStart!!.toLong(), zone)
            var endDate = startDate

            val (localEndDate, incompatible, daysOfWeek, repeatPeriod) =
                RRuleValues.getValues(rrule, startDate, zone)

            if (localEndDate != null) {
                endDate = EventsModel.EventDate(
                    localEndDate.year,
                    localEndDate.month,
                    localEndDate.dayOfMonth
                )
            }

            val end = LocalDate.of(endDate.year!!, endDate.month!!, endDate.day!!)
            if (!startDate.equals(endDate) && end.isBefore(LocalDate.now())) {
                continue // do not add expired events
            }

            val selected = events.size < EventsModel.MAX_REMINDERS
                events.add(
                    EventsModel.Event(
                        title,
                        startDate,
                        endDate,
                        repeatPeriod,
                        daysOfWeek,
                        true,
                        incompatible,
                        selected
                    )
                )
        }
        cur.close()

        return events
    }

    private object CalendarObserver {
        private var registered = false

        init {
            Looper.prepare()
        }

        private val calendarObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                ProgressEvents.onNext(ProgressEvents.Events.CalendarUpdated)
            }
        }

        fun register (cr: ContentResolver, uri: Uri) {
            if (!registered) {
                cr.registerContentObserver(uri, true, calendarObserver)
                registered = true
            }
        }
    }
}