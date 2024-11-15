/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockSmartSyncCompose.ui.events

import android.content.Context
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object EventsModel {

    const val MAX_REMINDERS = 5

    val events = ArrayList<Event>()

    fun refresh(context: Context) {
        events.clear()
        events.addAll(CalendarEvents.getEventsFromCalendar(context))
    }

    fun getEnabledCount(): Int {
        return (events.filter { it.enabled } as ArrayList<Event>).size
    }

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