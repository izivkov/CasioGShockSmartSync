package org.avmedia.gshockapi

import java.time.Month

class EventDate(var year: Int?, val month: Month?, val day: Int?) {
    fun equals(eventDate: EventDate): Boolean {
        return eventDate.year == year && eventDate.month == month && eventDate.day == day
    }
}