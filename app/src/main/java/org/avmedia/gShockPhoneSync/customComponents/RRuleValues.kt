/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-07, 10:24 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-07, 10:24 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import com.philjay.Frequency
import com.philjay.RRule
import com.philjay.WeekdayNum
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RRuleValues {
    data class Values(
        var localEndDate: LocalDate? = null,
        var incompatible: Boolean = false,
        var daysOfWeek: ArrayList<DayOfWeek>? = null,
        var repeatPeriod: EventsModel.RepeatPeriod = EventsModel.RepeatPeriod.NEVER
    )

    fun getValues(
        rrule: String?,
        startDate: EventsModel.EventDate,
        zone: ZoneId
    ): Values {
        val rruleValues = Values()

        if (rrule != null && rrule.isNotEmpty()) {

            val rruleObj = RRule(rrule)

            fun isCompatible(rruleObj: RRule): Boolean {
                val validNumberOnly = listOf<Int>(0)
                val numberArr = rruleObj.byDay.map { it.number }

                val validByMonth = rruleObj.byMonth.isEmpty()
                val validByDay = rruleObj.byDay.isEmpty() || validNumberOnly.containsAll(numberArr)
                val validInterval = rruleObj.interval != null && (rruleObj.interval != 0 || rruleObj.interval != 1)

                return validByMonth && validByDay && validInterval
            }

            if (!isCompatible(rruleObj)) {
                rruleValues.incompatible = true
                Timber.i("Event not compatible with Watch")
            }

            if (rrule != null && rrule.isNotEmpty()) {
                val rruleObj = RRule(rrule)
                if (rruleObj.until != null) {
                    val formatter: DateTimeFormatter =
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    val localDate =
                        LocalDateTime.parse(rruleObj.until.toString(), formatter).toLocalDate()
                    val instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()

                    rruleValues.localEndDate = instant.atZone(zone).toLocalDate()

                } else if (rruleObj.count != null) {
                    val numberOfPeriods: Long = (rruleObj.count - 1).toLong()
                    if (numberOfPeriods > 1) {
                        when (rruleObj.freq) {
                            Frequency.Daily -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year!!,
                                        startDate.month!!,
                                        startDate.day!!
                                    )
                                        .plusDays(numberOfPeriods)

                            }
                            Frequency.Weekly -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year!!,
                                        startDate.month!!,
                                        startDate.day!!
                                    )
                                        .plusWeeks(numberOfPeriods)
                            }
                            Frequency.Monthly -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year!!,
                                        startDate.month!!,
                                        startDate.day!!
                                    )
                                        .plusMonths(numberOfPeriods)
                            }
                            Frequency.Yearly -> {
                                rruleValues.localEndDate =
                                    LocalDate.of(
                                        startDate.year!!,
                                        startDate.month!!,
                                        startDate.day!!
                                    )
                                        .plusYears(numberOfPeriods)
                            }
                        }
                    }
                }

                rruleValues.repeatPeriod = toEventRepeatPeriod(rruleObj.freq)
                if (rruleValues.repeatPeriod == EventsModel.RepeatPeriod.WEEKLY) {
                    val weekDays = rruleObj.byDay
                    rruleValues.daysOfWeek = createDaysOfWeek(weekDays)
                }
            }
        }

        return rruleValues
    }

    private fun toEventRepeatPeriod(freq: Frequency): EventsModel.RepeatPeriod {
        when (freq) {
            Frequency.Monthly -> return EventsModel.RepeatPeriod.MONTHLY
            Frequency.Weekly -> return EventsModel.RepeatPeriod.WEEKLY
            Frequency.Yearly -> return EventsModel.RepeatPeriod.YEARLY
            Frequency.Daily -> return EventsModel.RepeatPeriod.DAILY
            else -> return EventsModel.RepeatPeriod.NEVER
        }
    }

    private fun createDaysOfWeek(weekDays: ArrayList<WeekdayNum>): ArrayList<DayOfWeek>? {
        var days: ArrayList<DayOfWeek>? = ArrayList()
        weekDays.forEach {
            when (it.weekday.name) {
                "Monday" -> days?.add(DayOfWeek.MONDAY)
                "Tuesday" -> days?.add(DayOfWeek.TUESDAY)
                "Wednesday" -> days?.add(DayOfWeek.WEDNESDAY)
                "Thursday" -> days?.add(DayOfWeek.THURSDAY)
                "Friday" -> days?.add(DayOfWeek.FRIDAY)
                "SATURDAY" -> days?.add(DayOfWeek.SATURDAY)
                else -> days?.add(DayOfWeek.SUNDAY)
            }
        }

        return days
    }
}