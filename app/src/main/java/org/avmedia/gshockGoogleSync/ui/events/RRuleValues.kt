/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-07, 10:24 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-07, 10:24 a.m.
 */

package org.avmedia.gshockGoogleSync.ui.events

import android.content.Context
import com.philjay.Frequency
import com.philjay.RRule
import com.philjay.Weekday
import com.philjay.WeekdayNum
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockapi.EventDate
import org.avmedia.gshockapi.RepeatPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RRuleValues @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    data class Values(
        val localEndDate: LocalDate? = null,
        val incompatible: Boolean = false,
        val daysOfWeek: List<DayOfWeek> = emptyList(),
        val repeatPeriod: RepeatPeriod = RepeatPeriod.NEVER
    )

    @Suppress("UNUSED_PARAMETER")
    private fun validateRule(rule: String): Boolean {
        // Add possible validation here.
        return true
    }

    fun getValues(
        rrule: String?,
        startDate: EventDate,
        zone: ZoneId
    ): Values = when {
        rrule.isNullOrEmpty() -> Values()
        !validateRule(rrule) -> Values(incompatible = true)
        else -> parseRRule(rruleUntilFix(rrule), startDate, zone)
    }

    private fun parseRRule(rrule: String, startDate: EventDate, zone: ZoneId): Values {
        val rruleObj = RRule(rrule)

        if (!isRRuleCompatible(rruleObj)) {
            return Values(incompatible = true)
        }

        val endDate = calculateEndDate(rruleObj, startDate, zone)
        val repeatPeriod = toEventRepeatPeriod(rruleObj.freq)
        val daysOfWeek = when (repeatPeriod) {
            RepeatPeriod.WEEKLY -> extractWeekDays(rruleObj, startDate)
            else -> emptyList()
        }

        return Values(
            localEndDate = endDate,
            repeatPeriod = repeatPeriod,
            daysOfWeek = daysOfWeek
        )
    }

    private fun isRRuleCompatible(rruleObj: RRule): Boolean {
        val validNumberOnly = listOf(0)
        val numberArr = rruleObj.byDay.map { it.number }

        return rruleObj.byMonth.isEmpty() &&
                (rruleObj.byDay.isEmpty() || validNumberOnly.containsAll(numberArr)) &&
                !(rruleObj.freq == Frequency.Weekly && rruleObj.interval > 1)
    }

    private fun extractWeekDays(rruleObj: RRule, startDate: EventDate): List<DayOfWeek> =
        if (rruleObj.byDay.isEmpty()) {
            listOf(LocalDate.of(startDate.year, startDate.month, startDate.day).dayOfWeek)
        } else {
            rruleObj.byDay.map { weekdayNum ->
                when (weekdayNum.weekday) {
                    Weekday.Monday -> DayOfWeek.MONDAY
                    Weekday.Tuesday -> DayOfWeek.TUESDAY
                    Weekday.Wednesday -> DayOfWeek.WEDNESDAY
                    Weekday.Thursday -> DayOfWeek.THURSDAY
                    Weekday.Friday -> DayOfWeek.FRIDAY
                    Weekday.Saturday -> DayOfWeek.SATURDAY
                    Weekday.Sunday -> DayOfWeek.SUNDAY
                }
            }
        }

    private fun calculateEndDate(rruleObj: RRule, startDate: EventDate, zone: ZoneId): LocalDate? {
        val startLocalDate = LocalDate.of(startDate.year, startDate.month, startDate.day)

        return when {
            rruleObj.until != null -> parseUntilDate(rruleObj.until.toString(), zone)
            rruleObj.count > 1 -> calculateEndDateFromCount(
                startLocalDate,
                rruleObj.count - 1,
                rruleObj.freq,
                rruleObj.byDay
            )

            else -> null
        }
    }

    private fun parseUntilDate(untilString: String, zone: ZoneId): LocalDate {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        return LocalDateTime.parse(untilString, formatter)
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .atZone(zone)
            .toLocalDate()
    }

    private fun calculateEndDateFromCount(
        startDate: LocalDate,
        count: Int,
        frequency: Frequency,
        byDay: ArrayList<WeekdayNum>
    ): LocalDate = when (frequency) {
        Frequency.Daily -> startDate.plusDays(count.toLong())
        Frequency.Weekly -> calculateWeeklyEndDate(startDate, byDay, count)
        Frequency.Monthly -> startDate.plusMonths(count.toLong())
        Frequency.Yearly -> startDate.plusYears(count.toLong())
        else -> startDate
    }

    private fun calculateWeeklyEndDate(
        startDate: LocalDate,
        weekDays: List<WeekdayNum>,
        count: Int
    ): LocalDate {
        if (weekDays.isEmpty()) return startDate

        val targetDays = weekDays.map { weekdayNum ->
            when (weekdayNum.weekday) {
                Weekday.Monday -> DayOfWeek.MONDAY
                Weekday.Tuesday -> DayOfWeek.TUESDAY
                Weekday.Wednesday -> DayOfWeek.WEDNESDAY
                Weekday.Thursday -> DayOfWeek.THURSDAY
                Weekday.Friday -> DayOfWeek.FRIDAY
                Weekday.Saturday -> DayOfWeek.SATURDAY
                Weekday.Sunday -> DayOfWeek.SUNDAY
            }
        }.toSet()

        return generateSequence(startDate) { it.plusDays(1) }
            .filter { targetDays.contains(it.dayOfWeek) }
            .drop(count)
            .first()
    }

    private fun toEventRepeatPeriod(freq: Frequency): RepeatPeriod = when (freq) {
        Frequency.Monthly -> RepeatPeriod.MONTHLY
        Frequency.Weekly -> RepeatPeriod.WEEKLY
        Frequency.Yearly -> RepeatPeriod.YEARLY
        Frequency.Daily -> RepeatPeriod.DAILY
        else -> RepeatPeriod.NEVER
    }

    private fun rruleUntilFix(rrule: String): String {
        val components = rrule.split(";", "=")
        val untilIndex = components.indexOf("UNTIL")
        if (untilIndex == -1) return rrule

        return components.getOrNull(untilIndex + 1)?.let { untilValue ->
            runCatching {
                DateTimeFormatter.ofPattern("yyyyMMdd").parse(untilValue)
                rrule.replace(untilValue, "${untilValue}T000000Z")
            }.getOrDefault(rrule)
        } ?: rrule
    }
}