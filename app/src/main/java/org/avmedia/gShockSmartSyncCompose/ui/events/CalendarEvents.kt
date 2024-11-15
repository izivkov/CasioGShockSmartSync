/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 6:13 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 6:13 p.m.
 */

package org.avmedia.gShockSmartSyncCompose.ui.events

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.provider.CalendarContract
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.applicationContext
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventDate
import org.avmedia.gshockapi.ProgressEvents
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object CalendarEvents {
    fun getEventsFromCalendar(context: Context): ArrayList<Event> {
        return getEvents(context)
    }

    @SuppressLint("Range")
    private fun getEvents(context: Context): ArrayList<Event> {
        val events: ArrayList<Event> = ArrayList()
        val cur: Cursor?
        val cr: ContentResolver = context.contentResolver

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,        // Event ID
            CalendarContract.Instances.TITLE,           // Event title
            CalendarContract.Instances.BEGIN,           // Event start time (next occurrence)
            CalendarContract.Instances.END,             // Event end time
            CalendarContract.Instances.ALL_DAY,         // Whether the event is an all-day event
            CalendarContract.Instances.RRULE,           // Recurrence rule (if any)
            CalendarContract.Instances.DTSTART,
        )

        val calendar: Calendar = Calendar.getInstance()
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.HOUR, 0)

        val selection =
            """
            (${CalendarContract.Events.DTEND} >= ${calendar.timeInMillis}
            or ${CalendarContract.Events.RRULE} IS NOT NULL)
            and (${CalendarContract.Events.CALENDAR_ACCESS_LEVEL} = ?
            or ${CalendarContract.Events.HAS_ALARM} = "1")
            """.trimIndent()

        // Use this for non-google calendar
        // or ${CalendarContract.Events.TITLE} = "LOCAL"

        val selectionArgs = arrayOf(
            CalendarContract.Calendars.CAL_ACCESS_OWNER.toString()
        )

        // Get the current time and define the end time (e.g., 1 year from now)
        val startMillis = Calendar.getInstance().timeInMillis
        val endMillis = Calendar.getInstance().apply {
            add(Calendar.YEAR, 2)  // Query up to 2 years in the future
        }.timeInMillis

        // Build the URI for querying instances within the specified time range
        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val uri = builder.build()

        // Sort by the BEGIN field (next occurrence)
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        // Perform the query to get event instances within the specified time range
        cur = cr.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        // check cusror columns:
        if (cur != null) {
            val columnNames = cur.columnNames
            columnNames.forEach { columnName ->
                println("Available column: $columnName")
            }

            while (cur.moveToNext()) {
                // Safely get column indices
                val titleColumnIndex = cur.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginColumnIndex = cur.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)

                val eventTitle = cur.getString(titleColumnIndex)
                val eventBegin = cur.getLong(beginColumnIndex)

                println("Event: $eventTitle, Start: $eventBegin")
            }
            // cur.close()
            cur.moveToFirst()
        }
        // end


        CalendarObserver.register(cr, uri)

        val seenEventIds = mutableSetOf<Long>() // Set to track seen EVENT_IDs

        while (cur!!.moveToNext()) {
            val eventId =
                cur.getLong(cur.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID))

            val eventStart =
                cur.getLong(cur.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
            val currentTimeMillis = System.currentTimeMillis()

            // If we haven't seen this EVENT_ID before, it's the first occurrence of the event
            if (eventId !in seenEventIds && eventStart > currentTimeMillis) {
                seenEventIds.add(eventId)

                var title: String? =
                    cur.getString(cur.getColumnIndex(CalendarContract.Events.TITLE))
                title = if (title.isNullOrBlank()) "(No title)" else title

                val dateStart: String? =
                    cur.getString(cur.getColumnIndex(CalendarContract.Events.DTSTART))
                val rrule: String? =
                    cur.getString(cur.getColumnIndex(CalendarContract.Events.RRULE))
                val allDay: String? =
                    cur.getString(cur.getColumnIndex(CalendarContract.Events.ALL_DAY))

                var zone = ZoneId.systemDefault()
                if (allDay == "1") {
                    zone = ZoneId.of("UTC")
                }

                val startDate = EventsModel.createEventDate(dateStart!!.toLong(), zone)
                var endDate = startDate

                val (localEndDate, incompatible, daysOfWeek, repeatPeriod) =
                    RRuleValues.getValues(rrule, startDate, zone)

                if (localEndDate != null) {
                    endDate = EventDate(
                        localEndDate.year,
                        localEndDate.month,
                        localEndDate.dayOfMonth
                    )
                }

                val end = LocalDate.of(endDate.year, endDate.month, endDate.day)
                if (!startDate.equals(endDate) && end.isBefore(LocalDate.now())) {
                    continue // do not add expired events
                }

                val enabled = events.size < EventsModel.MAX_REMINDERS
                events.add(
                    Event(
                        title,
                        startDate,
                        endDate,
                        repeatPeriod,
                        daysOfWeek,
                        enabled,
                        incompatible,
                    )
                )
            }
        }
        cur.close()
        return events
    }

    private object CalendarObserver {
        private var registered = false

        @Suppress("DEPRECATION")
        private val calendarObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                ProgressEvents.onNext("CalendarUpdated", getEvents(applicationContext()))
            }
        }

        fun register(cr: ContentResolver, uri: Uri) {
            if (!registered) {
                cr.registerContentObserver(uri, true, calendarObserver)
                registered = true
            }
        }
    }
}