package org.avmedia.gshockGoogleSync.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One day on the calendar: [hasDay] / [hasNight] tell which records exist. A day without any is shown empty. */
data class CalendarCell(val date: LocalDate, val hasDay: Boolean, val hasNight: Boolean)

/** Picks values for the charts and the calendar. A day without a record is null: a gap in the chart, never 0. */
object HistorySeries {
    /** [count] values for the days ending on [end] (oldest first); null where there is no record or the column is empty. */
    fun days(rows: List<DayRow>, end: LocalDate, count: Int, pick: (DayRow) -> Int?): List<Float?> {
        val byDate = rows.associateBy { it.date }
        return (count - 1 downTo 0).map { back -> byDate[end.minusDays(back.toLong()).toString()]?.let(pick)?.toFloat() }
    }

    fun nights(rows: List<NightRow>, end: LocalDate, count: Int, pick: (NightRow) -> Int?): List<Float?> {
        val byDate = rows.associateBy { it.date }
        return (count - 1 downTo 0).map { back -> byDate[end.minusDays(back.toLong()).toString()]?.let(pick)?.toFloat() }
    }

    /** Weeks (Monday first) of [month]; null cells pad the first and last week. */
    fun calendar(month: YearMonth, days: Collection<DayRow>, nights: Collection<NightRow>): List<List<CalendarCell?>> {
        val dayDates = days.filter { it.hasAnyValue() }.map { it.date }.toSet()
        val nightDates = nights.map { it.date }.toSet()
        val lead = month.atDay(1).dayOfWeek.let { (it.value - DayOfWeek.MONDAY.value + 7) % 7 }
        val cells: List<CalendarCell?> = List(lead) { null } + (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            CalendarCell(date, date.toString() in dayDates, date.toString() in nightDates)
        }
        return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }

    private fun DayRow.hasAnyValue() = listOf(steps, distanceM, kcal, activeMin, walkSec, runSec, stillSec, hrRest, hrMin, hrMax, spo2, stairsUp, stairsDown)
        .any { it != null }
}
