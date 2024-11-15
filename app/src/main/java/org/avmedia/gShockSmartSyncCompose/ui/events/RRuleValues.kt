/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-07, 10:24 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-07, 10:24 a.m.
 */

package org.avmedia.gShockSmartSyncCompose.ui.events

import com.philjay.Frequency
import com.philjay.RRule
import com.philjay.Weekday
import com.philjay.WeekdayNum
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import org.avmedia.gshockapi.EventDate
import org.avmedia.gshockapi.RepeatPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object RRuleValues {
    data class Values(
        var localEndDate: LocalDate? = null,
        var incompatible: Boolean = false,
        var daysOfWeek: ArrayList<DayOfWeek>? = null,
        var repeatPeriod: RepeatPeriod = RepeatPeriod.NEVER
    )

    @Suppress(
        "SpellCheckingInspection"
    )
    fun getValues(
        _rrule: String?,
        startDate: EventDate,
        zone: ZoneId
    ): Values {
        val rruleValues = Values()

        if (!_rrule.isNullOrEmpty()) {
            val rrule = rruleUntilFix(_rrule)

            if (!validateRule(rrule)) {
                rruleValues.incompatible = true
                return rruleValues
            }

            val rruleObj = RRule(rrule)

            fun isCompatible(rruleObj: RRule): Boolean {
                val validNumberOnly = listOf(0)
                val numberArr = rruleObj.byDay.map { it.number }

                val validByMonth = rruleObj.byMonth.isEmpty()
                val validByDay = rruleObj.byDay.isEmpty() || validNumberOnly.containsAll(numberArr)
                val invalidByWeekly = (rruleObj.freq == Frequency.Weekly) && (rruleObj.interval > 1)

                return validByMonth && validByDay && !invalidByWeekly
            }

            if (!isCompatible(rruleObj)) {
                rruleValues.incompatible = true
                AppSnackbar("Event not compatible with Watch")
            }

            if (rrule.isNotEmpty()) {
                val rruleObjVal = RRule(rrule)
                if (rruleObjVal.until != null) {
                    val formatter: DateTimeFormatter =
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    val localDate =
                        LocalDateTime.parse(rruleObjVal.until.toString(), formatter).toLocalDate()
                    val instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()

                    rruleValues.localEndDate = instant.atZone(zone).toLocalDate()

                } else {
                    val numberOfPeriods: Long = (rruleObjVal.count - 1).toLong()
                    if (numberOfPeriods > 0) {
                        when (rruleObjVal.freq) {
                            Frequency.Daily -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year,
                                        startDate.month,
                                        startDate.day
                                    )
                                        .plusDays(numberOfPeriods)
                            }

                            Frequency.Weekly -> {
                                val weekDays = rruleObjVal.byDay

                                rruleValues.localEndDate = calculateEndDate(
                                    LocalDate.of(
                                        startDate.year,
                                        startDate.month,
                                        startDate.day
                                    ),
                                    weekDays,
                                    numberOfPeriods.toInt()
                                )
                            }

                            Frequency.Monthly -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year,
                                        startDate.month,
                                        startDate.day
                                    )
                                        .plusMonths(numberOfPeriods)
                            }

                            Frequency.Yearly -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year,
                                        startDate.month,
                                        startDate.day
                                    )
                                        .plusYears(numberOfPeriods)
                            }
                        }
                    }
                }

                rruleValues.repeatPeriod = toEventRepeatPeriod(rruleObjVal.freq)
                if (rruleValues.repeatPeriod == RepeatPeriod.WEEKLY) {
                    if (rruleObjVal.byDay.isEmpty()) {
                        rruleValues.daysOfWeek = ArrayList<DayOfWeek>()
                        val dayOfWeek =
                            LocalDate.of(startDate.year, startDate.month, startDate.day).dayOfWeek
                        rruleValues.daysOfWeek!!.add(dayOfWeek)
                    } else {
                        val weekDays = rruleObjVal.byDay
                        rruleValues.daysOfWeek = createDaysOfWeek(weekDays)
                    }
                }
            }
        }

        return rruleValues
    }

    private fun calculateEndDate(
        startDate: LocalDate,
        daysOfWeek: ArrayList<WeekdayNum>,
        n: Int
    ): LocalDate {
        var endDate = startDate

        if (daysOfWeek.isEmpty()) {
            return endDate
        }

        val daysOfWeekLocalDay = daysOfWeek.map {
            when (it.weekday) {
                Weekday.Monday -> DayOfWeek.MONDAY
                Weekday.Tuesday -> DayOfWeek.TUESDAY
                Weekday.Wednesday -> DayOfWeek.WEDNESDAY
                Weekday.Thursday -> DayOfWeek.THURSDAY
                Weekday.Friday -> DayOfWeek.FRIDAY
                Weekday.Saturday -> DayOfWeek.SATURDAY
                Weekday.Sunday -> DayOfWeek.SUNDAY
            }
        }
        var count = 0
        while (count < n) {
            endDate = endDate.plusDays(1)
            if (daysOfWeekLocalDay.contains(endDate.dayOfWeek)) {
                count++
            }
        }
        return endDate
    }

    @Suppress("UNUSED_PARAMETER")
    private fun validateRule(rule: String): Boolean {
        // TODO: Add validation here.
        return true
    }

    private fun toEventRepeatPeriod(freq: Frequency): RepeatPeriod {
        return when (freq) {
            Frequency.Monthly -> RepeatPeriod.MONTHLY
            Frequency.Weekly -> RepeatPeriod.WEEKLY
            Frequency.Yearly -> RepeatPeriod.YEARLY
            Frequency.Daily -> RepeatPeriod.DAILY
            else -> RepeatPeriod.NEVER
        }
    }

    private fun createDaysOfWeek(weekDays: ArrayList<WeekdayNum>): ArrayList<DayOfWeek> {
        val days: ArrayList<DayOfWeek> = ArrayList()
        weekDays.forEach {
            when (it.weekday.name) {
                "Monday" -> days.add(DayOfWeek.MONDAY)
                "Tuesday" -> days.add(DayOfWeek.TUESDAY)
                "Wednesday" -> days.add(DayOfWeek.WEDNESDAY)
                "Thursday" -> days.add(DayOfWeek.THURSDAY)
                "Friday" -> days.add(DayOfWeek.FRIDAY)
                "Saturday" -> days.add(DayOfWeek.SATURDAY)
                else -> days.add(DayOfWeek.SUNDAY)
            }
        }

        return days
    }

    private fun rruleUntilFix(rrule: String): String {
        val components = rrule.split(";", "=")
        val index = components.indexOf("UNTIL")
        if (index == -1) return rrule

        val untilValue = components[index + 1]
        return try {
            DateTimeFormatter.ofPattern("yyyyMMdd").parse(untilValue)
            val newUntilValue = untilValue + "T000000Z"
            rrule.replace(untilValue, newUntilValue)
        } catch (_: DateTimeParseException) {
            rrule
        }
    }
}
