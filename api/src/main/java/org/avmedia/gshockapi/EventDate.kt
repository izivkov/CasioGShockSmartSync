package org.avmedia.gshockapi

import java.time.Month

data class EventDate(
    var year: Int, // keeping var for compatibility
    val month: Month,
    val day: Int
) {
    fun equals(eventDate: EventDate): Boolean {
        return eventDate.year == year &&
                eventDate.month == month &&
                eventDate.day == day
    }

    // Using copy to modify year instead of direct mutation
    fun withYear(newYear: Int): EventDate = copy(year = newYear)
}
