/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.events

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventDate
import java.time.*
import java.util.*

object EventsModel {

    const val MAX_REMINDERS = 5

    val events = ArrayList<Event>()

    fun createEvent(event: Event) {
        events.add(event)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun refresh(context: Context) {
        events.clear()
        events.addAll(CalendarEvents.getEventsFromCalendar(context))
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun createEventDate(timeMs: Long, zone: ZoneId): EventDate {
        val start: LocalDate =
            Instant.ofEpochMilli(timeMs).atZone(zone)
                .toLocalDate()
        return EventDate(
            start.year,
            start.month,
            start.dayOfMonth
        )
    }
}