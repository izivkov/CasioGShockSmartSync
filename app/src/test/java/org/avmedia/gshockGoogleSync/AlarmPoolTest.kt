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

    // ---- one-time alarms ----

    private val nap = PhoneAlarm(id = "nap", hour = 15, minute = 0, once = true)

    @Test fun armSetsTodayWhenStillAhead() =
        assertEquals(java.time.LocalDate.of(2026, 11, 16), AlarmPool.arm(nap, at(16, 14)).onceDate)

    @Test fun armSetsTomorrowWhenPassed() =
        assertEquals(java.time.LocalDate.of(2026, 11, 17), AlarmPool.arm(nap, at(16, 15, 0)).onceDate)

    @Test fun armClearsDateOfRepeatingAlarm() =
        assertEquals(null, AlarmPool.arm(cats1.copy(onceDate = java.time.LocalDate.of(2026, 11, 16)), at(16, 9)).onceDate)

    @Test fun oneTimeRingsOnlyOnItsDay() {
        val armed = AlarmPool.arm(nap, at(16, 14))
        assertEquals(at(16, 15), AlarmPool.nextRing(armed, at(16, 14)))
        assertEquals(null, AlarmPool.nextRing(armed, at(16, 16))) // not again tomorrow
    }

    @Test fun oneTimeLoadedOnWatchBeforeItRings() {
        val armed = AlarmPool.arm(nap, at(16, 14))
        assertTrue("nap" in AlarmPool.plan(listOf(armed), List(AlarmPool.SLOTS) { null }, at(16, 14)))
    }

    @Test fun oneTimeKeptDuringSnoozeHold() {
        val armed = AlarmPool.arm(nap, at(16, 14))
        assertEquals(listOf(armed), AlarmPool.expireOnce(listOf(armed), at(16, 15, 30))) // 30 min < 40 min hold
    }

    @Test fun oneTimeSwitchedOffAfterSnoozeHold() {
        val armed = AlarmPool.arm(nap, at(16, 14))
        val after = AlarmPool.expireOnce(listOf(armed, cats1), at(16, 15, 40))
        assertEquals(false, after[0].enabled)
        assertEquals(cats1, after[1]) // repeating alarms untouched
        assertTrue(AlarmPool.plan(after, List(AlarmPool.SLOTS) { "nap" }, at(16, 15, 40)).none { it == "nap" })
    }

    @Test fun oneTimeReArmedWhenSwitchedBackOn() {
        val off = AlarmPool.arm(nap, at(16, 14)).copy(enabled = false)
        assertEquals(java.time.LocalDate.of(2026, 11, 17), AlarmPool.arm(off.copy(enabled = true), at(16, 18)).onceDate)
    }

    @Test fun oneTimeWithoutSnoozeSwitchedOffRightAfterRing() {
        val armed = AlarmPool.arm(nap.copy(snooze = false), at(16, 14))
        assertEquals(true, AlarmPool.expireOnce(listOf(armed), at(16, 15, 0))[0].enabled) // ringing now
        assertEquals(false, AlarmPool.expireOnce(listOf(armed), at(16, 15, 1))[0].enabled) // 1 min later
    }

    @Test fun soundModeNotHeldAfterAlarmWithoutSnooze() {
        // wake (vibe only, 08:10) without snooze: at 08:12 the next alarm (cats1, sound) governs.
        val noSnooze = wake.copy(snooze = false)
        assertEquals(SoundMode.CUSTOM, AlarmPool.desiredSoundMode(listOf(noSnooze, cats1), at(16, 8, 12)))
        assertEquals(SoundMode.VIBE_ONLY, AlarmPool.desiredSoundMode(listOf(wake, cats1), at(16, 8, 12))) // with snooze: held
    }

    // ---- snooze window / stop snooze ----

    @Test fun snoozingOnlyWithinWindowAndWithSnoozeOn() {
        val list = listOf(cats1, cats2.copy(snooze = false))
        assertEquals(listOf("cats1"), AlarmPool.snoozing(list, at(16, 12, 45)).map { it.first.id })
        assertEquals(emptyList<String>(), AlarmPool.snoozing(list, at(16, 13, 15)).map { it.first.id }) // window over
        assertEquals(emptyList<String>(), AlarmPool.snoozing(list, at(16, 20, 25)).map { it.first.id }) // no snooze
    }

    @Test fun dismissEndsTheHoldNow() {
        val after = AlarmPool.dismiss(listOf(cats1), "cats1", at(16, 12, 35))
        assertEquals(at(16, 12, 30), after[0].dismissedRing)
        assertTrue(AlarmPool.snoozing(after, at(16, 12, 35)).isEmpty())
        assertEquals(AlarmPool.snoozing(listOf(cats1), at(16, 12, 35)).size, 1) // without the stop: still snoozing
    }

    @Test fun dismissOnlyAffectsThatRing() {
        val after = AlarmPool.dismiss(listOf(cats1), "cats1", at(16, 12, 35))
        assertEquals(1, AlarmPool.snoozing(after, at(17, 12, 40)).size) // next day's ring snoozes normally
    }

    @Test fun dismissedOneTimeAlarmSwitchesOff() {
        val armed = AlarmPool.arm(nap, at(16, 14))
        val stopped = AlarmPool.dismiss(listOf(armed), "nap", at(16, 15, 3))
        assertEquals(false, AlarmPool.expireOnce(stopped, at(16, 15, 3))[0].enabled)
    }
}
