package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.h5600.AlarmPool
import org.avmedia.gshockGoogleSync.h5600.AlarmPool.PhoneAlarm
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.SoundMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime

class AlarmPoolTest {
    // 2026-11-16 = Monday, 20 = Friday, 21 = Saturday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

    private val wake = PhoneAlarm(id = "wake", hour = 8, minute = 10, days = weekdays, vibeOnly = true)
    private val cats1 = PhoneAlarm(id = "cats1", hour = 12, minute = 30)
    private val cats2 = PhoneAlarm(id = "cats2", hour = 20, minute = 20)
    private val pills = PhoneAlarm(id = "pills", hour = 9, minute = 0)
    private val tea = PhoneAlarm(id = "tea", hour = 16, minute = 0)
    private val sat = PhoneAlarm(id = "sat", hour = 10, minute = 0, days = setOf(SATURDAY, SUNDAY))
    private val pool = listOf(wake, cats1, cats2, pills, tea, sat) // 6 alarms > 4 slots

    private fun empty() = List<String?>(4) { null }

    @Test fun mondayMorningLoadsNextFour() =
        assertEquals(listOf("wake", "pills", "cats1", "tea"), AlarmPool.plan(pool, empty(), at(16, 7, 0)))

    @Test fun afterWakeRangSnoozeKeepsSlotAndCats2Waits() {
        val plan = AlarmPool.plan(pool, listOf("wake", "pills", "cats1", "tea"), at(16, 8, 11))
        assertEquals(listOf("wake", "pills", "cats1", "tea"), plan)
    }

    @Test fun afterSnoozeHoldCats2TakesFreedSlot() {
        val plan = AlarmPool.plan(pool, listOf("wake", "pills", "cats1", "tea"), at(16, 8, 51))
        assertEquals(listOf("cats2", "pills", "cats1", "tea"), plan) // wake's next ring is tomorrow 8:10 (<24h) but later than the others
    }

    @Test fun weekendAlarmNotLoadedOnWeekday() =
        assertTrue("sat" !in AlarmPool.plan(pool, empty(), at(16, 7, 0)))

    @Test fun weekdayAlarmNotLoadedFridayNight() =
        assertTrue("wake" !in AlarmPool.plan(pool, empty(), at(20, 21, 0)))

    @Test fun saturdayAlarmWaitsWhenFourAreDueSooner() =
        assertTrue("sat" !in AlarmPool.plan(pool, empty(), at(20, 10, 1)))

    @Test fun saturdayAlarmLoadedOnceASlotFrees() =
        assertTrue("sat" in AlarmPool.plan(pool, empty(), at(20, 21, 0))) // after 20:20 rang

    @Test fun neverLoadsAlarmThatWouldRingEarlierThanIntended() {
        // Friday 07:00: Saturday's 10:00 alarm is 27 h away -> must not be in a daily slot yet
        assertTrue("sat" !in AlarmPool.plan(pool, empty(), at(20, 7, 0)))
    }

    @Test fun toWatchDisablesUnusedSlots() {
        val watch = List(5) { Alarm(0, 0, enabled = true) }
        val w = AlarmPool.toWatchAlarms(listOf("cats1", null, null, null), pool, watch)
        assertEquals(Alarm(12, 30, enabled = true, snooze = true), w[0])
        assertEquals(false, w[1].enabled)
        assertEquals(watch[4], w[4]) // 5th slot untouched
    }

    @Test fun soundFollowsNextAlarm() {
        assertEquals(SoundMode.VIBE_ONLY, AlarmPool.desiredSoundMode(pool, at(15, 22, 0)))
        assertEquals(SoundMode.CUSTOM, AlarmPool.desiredSoundMode(pool, at(16, 8, 51)))
    }

    // Smart alarms: a smart 08:10 Mon-Fri (window 30) plus normal alarms.
    private val smartWake = wake.copy(id = "smartWake", smartWindowMin = 30)
    private val smartPool = listOf(smartWake, cats1, cats2)

    @Test fun smartAlarmNeverInNormalSlots() =
        assertTrue("smartWake" !in AlarmPool.plan(smartPool, empty(), at(16, 7, 0)))

    @Test fun smartSlotTakesNextSmartWithin24h() =
        assertEquals("smartWake", AlarmPool.planSmart(smartPool, null, at(15, 22, 0))) // Sun 22:00 -> Mon 08:10

    @Test fun smartSlotEmptyOnFridayNight() =
        assertEquals(null, AlarmPool.planSmart(smartPool, null, at(20, 22, 0)))

    @Test fun smartSlotKeptDuringSnoozeHold() =
        assertEquals("smartWake", AlarmPool.planSmart(smartPool, "smartWake", at(16, 8, 30)))

    @Test fun soundModeSwitchesBeforeSmartWindow() {
        val vibeSmart = smartWake.copy(vibeOnly = true)
        // 07:45 is inside the 30-min window before 08:10 -> must already be vibe-only
        assertEquals(SoundMode.VIBE_ONLY, AlarmPool.desiredSoundMode(listOf(vibeSmart, cats1), at(16, 7, 45)))
    }
}
