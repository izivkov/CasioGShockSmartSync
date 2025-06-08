/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gshockGoogleSync.ui.events

import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.EventDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object EventsModel {
    const val MAX_REMINDERS = 5
    private var _events = ArrayList<Event>()
    val events: ArrayList<Event>
        get() = _events

    fun refresh(newEvents: ArrayList<Event>) {
        _events = newEvents
    }

    fun createEventDate(timeMs: Long, zone: ZoneId): EventDate {
        val start: LocalDate = Instant.ofEpochMilli(timeMs)
            .atZone(zone)
            .toLocalDate()

        return EventDate(
            start.year,
            start.month,
            start.dayOfMonth
        )
    }
}