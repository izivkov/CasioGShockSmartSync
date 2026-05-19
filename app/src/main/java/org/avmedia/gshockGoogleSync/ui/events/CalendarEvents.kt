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
        val cursor = cr.query(
            uri,
            getProjection(),
            buildSelection(),
            null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        val excludedCalendarIds = getExcludedCalendarIds(cr)

        calendarObserver.register(cr, uri)
        cursor?.let {
            processCursor(it, events, excludedCalendarIds)
        }
        cursor?.close()

        return events
    }

    private fun getExcludedCalendarIds(cr: ContentResolver): Set<Long> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        val cursor = cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        )

        val ids = mutableSetOf<Long>()
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1) ?: ""
                val accountType = it.getString(2) ?: ""
                val accessLevel = it.getInt(3)

                // Only apply accessLevel filter for Google accounts where 700 semantics are known
                if (accountType == "com.google" && accessLevel < 700) ids.add(id)

                // Exclude birthday calendars regardless of provider
                if (name.contains("birthday", ignoreCase = true)) ids.add(id)
            }
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
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Events.CUSTOM_APP_PACKAGE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ORGANIZER,
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

    private fun processCursor(cursor: Cursor, events: ArrayList<Event>, excludedCalendarIds: Set<Long>) {
        val seenEventIds = mutableSetOf<Long>()
        cursor.moveToPosition(-1)

        while (cursor.moveToNext()) {
            val eventId =
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID))
            val eventStart =
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
            val calendarId =
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID))

            if (calendarId in excludedCalendarIds) continue

            if (eventId !in seenEventIds && eventStart > System.currentTimeMillis()) {
                seenEventIds.add(eventId)
                processEvent(cursor, events)
            }
        }
    }

    private fun processEvent(cursor: Cursor, events: ArrayList<Event>) {
        val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
        val title: String = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "(No title)" else "(No title)"

        val calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID))
        val appPackage = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.CUSTOM_APP_PACKAGE))
        val description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION))
        val organizer = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.ORGANIZER))

        android.util.Log.d("CalendarDebug",
            "Event: title=$title, " +
                    "calendarId=$calendarId, " +
                    "appPackage=$appPackage, " +
                    "description=$description, " +
                    "organizer=$organizer"
        )

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