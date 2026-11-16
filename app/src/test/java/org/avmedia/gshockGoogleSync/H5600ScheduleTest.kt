package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.h5600.H5600Schedule
import org.avmedia.gshockGoogleSync.h5600.H5600Schedule.Config
import org.avmedia.gshockapi.model.SoundMode.CUSTOM
import org.avmedia.gshockapi.model.SoundMode.VIBE_ONLY
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class H5600ScheduleTest {
    // 2026-11-16 is a Monday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private val overnight = Config(enabled = true, startMin = 22 * 60, endMin = 8 * 60)

    @Test fun sundayNightBeforeMondayIsVibe() = assertEquals(VIBE_ONLY, H5600Schedule.desiredMode(overnight, at(15, 23)))
    @Test fun mondayEarlyMorningIsVibe() = assertEquals(VIBE_ONLY, H5600Schedule.desiredMode(overnight, at(16, 7, 59)))
    @Test fun mondayAtEndIsCustom() = assertEquals(CUSTOM, H5600Schedule.desiredMode(overnight, at(16, 8, 0)))
    @Test fun mondayAfternoonIsCustom() = assertEquals(CUSTOM, H5600Schedule.desiredMode(overnight, at(16, 15)))
    @Test fun fridayNightIsCustom() = assertEquals(CUSTOM, H5600Schedule.desiredMode(overnight, at(20, 23)))
    @Test fun saturdayMorningIsCustom() = assertEquals(CUSTOM, H5600Schedule.desiredMode(overnight, at(21, 6)))
    @Test fun thursdayNightIsVibe() = assertEquals(VIBE_ONLY, H5600Schedule.desiredMode(overnight, at(19, 22)))

    private val sameDay = Config(enabled = true, startMin = 5 * 60, endMin = 9 * 60)

    @Test fun sameDayWeekdayInside() = assertEquals(VIBE_ONLY, H5600Schedule.desiredMode(sameDay, at(17, 6)))
    @Test fun sameDayWeekdayOutside() = assertEquals(CUSTOM, H5600Schedule.desiredMode(sameDay, at(17, 4)))
    @Test fun sameDaySunday() = assertEquals(CUSTOM, H5600Schedule.desiredMode(sameDay, at(22, 6)))

    // User's case: 8:10 workday alarm vibe, 12:30 and 20:20 with sound; window 20:30 -> 09:00.
    private val user = Config(enabled = true, startMin = 20 * 60 + 30, endMin = 9 * 60)

    @Test fun userMonday0810Vibe() = assertEquals(VIBE_ONLY, H5600Schedule.desiredMode(user, at(16, 8, 10)))
    @Test fun userMonday1230Sound() = assertEquals(CUSTOM, H5600Schedule.desiredMode(user, at(16, 12, 30)))
    @Test fun userMonday2020Sound() = assertEquals(CUSTOM, H5600Schedule.desiredMode(user, at(16, 20, 20)))
    @Test fun userSaturday0810Sound() = assertEquals(CUSTOM, H5600Schedule.desiredMode(user, at(21, 8, 10)))
    @Test fun userSunday2020Sound() = assertEquals(CUSTOM, H5600Schedule.desiredMode(user, at(22, 20, 20)))

    @Test fun nextBoundaryEvening() =
        assertEquals(at(16, 20, 30), H5600Schedule.nextBoundary(user, at(16, 12, 30)))
    @Test fun nextBoundaryAfterStart() =
        assertEquals(at(17, 9, 0), H5600Schedule.nextBoundary(user, at(16, 20, 30)))
    @Test fun nextBoundaryMorning() =
        assertEquals(at(17, 9, 0), H5600Schedule.nextBoundary(user, at(17, 3, 0)))

    // Alarm day rules. 2026-11-20 = Friday, 21 = Saturday, 22 = Sunday, 18 = Wednesday.
    private val wake = org.avmedia.gshockapi.model.Alarm(8, 10, enabled = true)
    private val R = org.avmedia.gshockGoogleSync.h5600.AlarmDayRules
    private val weekdays = mapOf(8 * 60 + 10 to setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
        java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY))
    private val mwf = mapOf(8 * 60 + 10 to setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY))
    private val weekend = mapOf(8 * 60 + 10 to setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY))
    private fun on(rules: Map<Int, Set<java.time.DayOfWeek>>, now: LocalDateTime) = R.apply(listOf(wake), rules, now)[0].enabled

    @Test fun weekdayAlarmOnBeforeFridayRing() = assertEquals(true, on(weekdays, at(20, 7, 0)))
    @Test fun weekdayAlarmOffAfterFridayRing() = assertEquals(false, on(weekdays, at(20, 8, 11)))
    @Test fun weekdayAlarmOffSaturday() = assertEquals(false, on(weekdays, at(21, 12, 0)))
    @Test fun weekdayAlarmOnSundayEvening() = assertEquals(true, on(weekdays, at(22, 20, 0)))
    @Test fun mwfOffTuesdayRing() = assertEquals(false, on(mwf, at(17, 7, 0)))
    @Test fun mwfOnWednesdayRing() = assertEquals(true, on(mwf, at(17, 9, 0)))
    @Test fun weekendOnSaturday() = assertEquals(true, on(weekend, at(21, 7, 0)))
    @Test fun weekendOffAfterSundayRing() = assertEquals(false, on(weekend, at(22, 9, 0)))
    @Test fun everyDayUntouched() = assertEquals(false, R.apply(listOf(wake.copy(enabled = false)), emptyMap(), at(20, 7))[0].enabled)
    // Per-alarm sound, user's setup: #0 08:10 Mon–Fri vibe-only, #1 12:30 and #2 20:20 every day with sound.
    private val userAlarms = listOf(
        org.avmedia.gshockapi.model.Alarm(8, 10, enabled = true),
        org.avmedia.gshockapi.model.Alarm(12, 30, enabled = true),
        org.avmedia.gshockapi.model.Alarm(20, 20, enabled = true),
    )
    private fun mode(now: LocalDateTime) = R.desiredSoundMode(userAlarms, weekdays, setOf(8 * 60 + 10), now)

    @Test fun sundayNightVibeForMonday() = assertEquals(VIBE_ONLY, mode(at(22, 21, 0)))
    @Test fun mondayMorningVibe() = assertEquals(VIBE_ONLY, mode(at(16, 7, 0)))
    @Test fun mondaySnoozeStillVibe() = assertEquals(VIBE_ONLY, mode(at(16, 8, 40)))
    @Test fun mondayAfterSnoozeSound() = assertEquals(CUSTOM, mode(at(16, 8, 51)))
    @Test fun mondayEveningSoundFor2020() = assertEquals(CUSTOM, mode(at(16, 19, 0)))
    @Test fun mondayAfter2020SnoozeVibeForTuesday() = assertEquals(VIBE_ONLY, mode(at(16, 21, 1)))
    @Test fun fridayNightSoundForSaturday1230() = assertEquals(CUSTOM, mode(at(20, 22, 0)))
    @Test fun noVibeAlarmsLeavesModeAlone() =
        assertEquals(null, R.desiredSoundMode(userAlarms, weekdays, emptySet(), at(16, 7, 0)))

    // Regression (user test 2026-11-16): 19:40 sound alarm, then 19:44 vibe-only alarm.
    private val closeAlarms = listOf(
        org.avmedia.gshockapi.model.Alarm(19, 40, enabled = true),
        org.avmedia.gshockapi.model.Alarm(19, 44, enabled = true),
    )
    @Test fun upcomingAlarmBeatsSnoozeHold() =
        assertEquals(VIBE_ONLY, R.desiredSoundMode(closeAlarms, emptyMap(), setOf(19 * 60 + 44), at(16, 19, 42)))
    @Test fun holdKeptAfterLastAlarm() =
        assertEquals(VIBE_ONLY, R.desiredSoundMode(closeAlarms, emptyMap(), setOf(19 * 60 + 44), at(16, 19, 50)))

    // Settings follow the alarm's time, not its slot (the Alarms tab re-sorts alarms).
    @Test fun reorderedAlarmsKeepTheirSettings() = assertEquals(
        VIBE_ONLY,
        R.desiredSoundMode(userAlarms.reversed(), weekdays, setOf(8 * 60 + 10), at(16, 7, 0)),
    )

    @Test fun labels() {
        assertEquals("Every day", R.label(null))
        assertEquals("Mon–Fri", R.label(weekdays.values.first()))
        assertEquals("Mon, Wed, Fri", R.label(mwf.values.first()))
        assertEquals("Sat, Sun", R.label(weekend.values.first()))
    }
}
