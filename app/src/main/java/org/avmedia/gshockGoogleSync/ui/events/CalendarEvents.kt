/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 6:13 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 6:13 p.m.
 */

package org.avmedia.gshockGoogleSync.ui.events

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventDate
import org.avmedia.gshockapi.ProgressEvents
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEvents @Inject constructor(
    private val rRuleValues: RRuleValues,
    @param:ApplicationContext private val appContext: Context
) {
    private val calendarObserver = CalendarObserver()

    fun getEventsFromCalendar(): ArrayList<Event> {
        return getEvents(appContext)
    }

    @SuppressLint("Range")
    private fun getEvents(context: Context): ArrayList<Event> {
        val events: ArrayList<Event> = ArrayList()
        val cr: ContentResolver = context.contentResolver

        val uri = buildCalendarUri()
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_OWNER.toString())
        val cursor = cr.query(
            uri,
            getProjection(),
            buildSelection(),
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        val birthdayCalendarIds = getBirthdayCalendarIds(cr)

        calendarObserver.register(cr, uri)
        cursor?.let {
            processCursor(it, events, birthdayCalendarIds)
        }
        cursor?.close()

        return events
    }

    private fun getBirthdayCalendarIds(cr: ContentResolver): Set<Long> {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = """
            ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?
            AND LOWER(${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}) LIKE ?
        """
        val selectionArgs = arrayOf("com.google", "%birthday%")

        val cursor = cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        val ids = mutableSetOf<Long>()
        cursor?.use {
            while (it.moveToNext()) ids.add(it.getLong(0))
        }
        return ids
    }

    private fun buildCalendarUri(): Uri {
        val startMillis = Calendar.getInstance().timeInMillis
        val endMillis = Calendar.getInstance()
            .apply { add(Calendar.YEAR, 2) }.timeInMillis

        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        return builder.build()
    }

    private fun getProjection(): Array<String> {
        return arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.DTSTART,
            CalendarContract.Instances.CALENDAR_ID
        )
    }

    private fun buildSelection(): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.MILLISECOND, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.HOUR, 0)
        }

        return """
            (${CalendarContract.Events.DTEND} >= ${calendar.timeInMillis}
            or ${CalendarContract.Events.RRULE} IS NOT NULL)
            and (${CalendarContract.Events.CALENDAR_ACCESS_LEVEL} = ?
            or ${CalendarContract.Events.HAS_ALARM} = "1")
        """.trimIndent()
    }

    private fun processCursor(cursor: Cursor, events: ArrayList<Event>, birthdayCalendarIds: Set<Long>) {
        val seenEventIds = mutableSetOf<Long>()
        cursor.moveToPosition(-1)

        while (cursor.moveToNext()) {
            val eventId =
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID))
            val eventStart =
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
            val calendarId =
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID))

            // Skip birthday calendar events, with title check as fallback
            if (calendarId in birthdayCalendarIds) continue

            if (eventId !in seenEventIds && eventStart > System.currentTimeMillis()) {
                seenEventIds.add(eventId)
                processEvent(cursor, events)
            }
        }
    }

    private fun processEvent(cursor: Cursor, events: ArrayList<Event>) {
        val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
        val title: String = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "(No title)" else "(No title)"

        if (title.equals("Birthday", ignoreCase = true) || title.equals("Birthday Vents", ignoreCase = true)) {
            return
        }

        val dateStartIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
        val dateStart: String? = if (dateStartIndex >= 0) cursor.getString(dateStartIndex) else null

        val rruleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
        val rrule: String? = if (rruleIndex >= 0) cursor.getString(rruleIndex) else null

        val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
        val allDay: String? = if (allDayIndex >= 0) cursor.getString(allDayIndex) else null

        val zone = if (allDay == "1") ZoneId.of("UTC") else ZoneId.systemDefault()

        val startDate = EventsModel.createEventDate(dateStart!!.toLong(), zone)
        var endDate = startDate

        val (localEndDate, incompatible, daysOfWeek, repeatPeriod) = rRuleValues.getValues(
            rrule,
            startDate,
            zone
        )

        localEndDate?.let {
            endDate = EventDate(it.year, it.month, it.dayOfMonth)
        }

        val end = LocalDate.of(endDate.year, endDate.month, endDate.day)
        if (!startDate.equals(endDate) && end.isBefore(LocalDate.now())) return

        val enabled = events.size < EventsModel.MAX_REMINDERS
        events.add(
            Event(
                title,
                startDate,
                endDate,
                repeatPeriod,
                daysOfWeek,
                enabled,
                incompatible
            )
        )
    }

    inner class CalendarObserver {
        private var registered = false

        @Suppress("DEPRECATION")
        private val calendarObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                ProgressEvents.onNext("CalendarUpdated", getEvents(appContext))
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
