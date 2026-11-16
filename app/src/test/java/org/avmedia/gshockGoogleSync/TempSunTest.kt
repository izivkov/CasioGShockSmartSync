package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.h5600.TempSun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class TempSunTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val lat = 38.7223; private val lon = -9.1393

    /** For every temperature: fake sunrise reads 0:TT local and fake sunset = real sunset (±1 min). */
    private fun checkDate(date: LocalDate) {
        val (_, realSet) = TempSun.riseSetUtcMin(date, lat, lon)!!
        val realSetLocal = TempSun.toLocal(date, realSet, lisbon)
        for (t in 0..TempSun.MAX_TEMP) {
            val fake = TempSun.fakeCoordinates(date, lat, lon, lisbon, t)
            assertNotNull("no solution for $t °C on $date", fake)
            val (r, s) = TempSun.riseSetUtcMin(date, fake!!.first, fake.second, TempSun.WATCH_H0)!!
            val rise = TempSun.toLocal(date, r, lisbon)
            val set = TempSun.toLocal(date, s, lisbon)
            assertEquals("$t °C on $date: sunrise hour", 0, rise.hour)
            assertEquals("$t °C on $date: sunrise minute", t, rise.minute + if (rise.second >= 30) 1 else 0)
            assertEquals("$t °C on $date: sunset", 0L, Duration.between(realSetLocal, set).abs().toMinutes())
        }
    }

    @Test fun winter() = checkDate(LocalDate.of(2026, 11, 16))
    @Test fun summer() = checkDate(LocalDate.of(2026, 6, 21)) // Lisbon UTC+1, sunrise wraps to previous UTC day
    @Test fun springDstDay() = checkDate(LocalDate.of(2026, 3, 29))
    @Test fun clampsAbove59() = assertEquals(
        TempSun.fakeCoordinates(LocalDate.of(2026, 11, 16), lat, lon, lisbon, 59),
        TempSun.fakeCoordinates(LocalDate.of(2026, 11, 16), lat, lon, lisbon, 75),
    )

    /** Corrections move our formula's targets: rise later by riseCorr, sunset earlier by -setCorr. */
    @Test fun correctionsShiftTargets() {
        val d = LocalDate.of(2026, 11, 16)
        val (_, realSet) = TempSun.riseSetUtcMin(d, lat, lon)!!
        val fake = TempSun.fakeCoordinates(d, lat, lon, lisbon, 18, riseCorrMin = 2, setCorrMin = -3)!!
        val (r, s) = TempSun.riseSetUtcMin(d, fake.first, fake.second, TempSun.WATCH_H0)!!
        assertEquals(20.25, r, 0.1)             // 0:18 + 2 min + 15 s aim (Lisbon = UTC in November)
        assertEquals(realSet - 3 + 0.25, s, 0.1)
    }

    /** Weather in the hour: sunrise W:TT for W = 1..3 (sunny / cloudy / rain), sunset stays real. */
    private fun checkWeather(date: LocalDate) {
        val (_, realSet) = TempSun.riseSetUtcMin(date, lat, lon)!!
        val realSetLocal = TempSun.toLocal(date, realSet, lisbon)
        for (w in 1..4) for (t in 0..TempSun.MAX_TEMP) {
            // Spring-forward day: local 1:00-1:59 does not exist in Lisbon, so W=1 cannot be shown
            // (the watch would show 2:TT). Accepted: one day a year.
            if (lisbon.rules.getValidOffsets(date.atTime(w, t)).isEmpty()) continue
            val fake = TempSun.fakeCoordinates(date, lat, lon, lisbon, t, riseHour = w)
            assertNotNull("no solution for $w:$t on $date", fake)
            val (r, s) = TempSun.riseSetUtcMin(date, fake!!.first, fake.second, TempSun.WATCH_H0)!!
            val rise = TempSun.toLocal(date, r, lisbon)
            assertEquals("$w:$t on $date hour", w, rise.hour + if (rise.minute == 59 && rise.second >= 30) 1 else 0)
            assertEquals("$w:$t on $date minute", t, (rise.minute + if (rise.second >= 30) 1 else 0) % 60)
            assertEquals("$w:$t on $date sunset", 0L,
                Duration.between(realSetLocal, TempSun.toLocal(date, s, lisbon)).abs().toMinutes())
        }
    }

    @Test fun weatherWinter() = checkWeather(LocalDate.of(2026, 11, 16))
    @Test fun weatherSummer() = checkWeather(LocalDate.of(2026, 6, 21))
    @Test fun weatherDstDay() = checkWeather(LocalDate.of(2026, 3, 29))

    @Test fun categories() {
        assertEquals(1, TempSun.category(0)); assertEquals(1, TempSun.category(1))
        assertEquals(2, TempSun.category(3)); assertEquals(2, TempSun.category(45))
        assertEquals(3, TempSun.category(61)); assertEquals(3, TempSun.category(95)); assertEquals(3, TempSun.category(73))
    }

    /** The fitted watch model reproduces the user's two readings (whole minutes, ±1). */
    @Test fun watchModelMatchesReadings() {
        val d = LocalDate.of(2026, 11, 16)
        val (r1, s1) = TempSun.riseSetUtcMin(d, -60.133, 43.468, TempSun.WATCH_H0)!!
        assertEquals(16.0, r1, 1.0); assertEquals(17 * 60 + 26.0, s1, 1.0)
        val (r2, s2) = TempSun.riseSetUtcMin(d, -35.587, 21.096, TempSun.WATCH_H0)!!
        assertEquals(3 * 60 + 19.0, r2, 1.0); assertEquals(17 * 60 + 21.0, s2, 1.0)
    }

    @Test fun fetchesJustAfterEachQuarterHour() {
        val t = { h: Int, m: Int -> java.time.LocalDateTime.of(2026, 11, 16, h, m) }
        assertEquals(t(21, 16), TempSun.nextFetch(t(21, 2)))
        assertEquals(t(21, 1), TempSun.nextFetch(t(21, 0)))
        assertEquals(t(21, 16), TempSun.nextFetch(t(21, 1)))
        assertEquals(t(22, 1), TempSun.nextFetch(t(21, 59)))
    }

    @Test fun categoryFromCodeAndAmounts() {
        assertEquals(3, TempSun.category(61, 0.0, 100)) // code says rain with 0.0 mm: it was raining
        assertEquals(3, TempSun.category(3, 0.2, 100))  // amount without a rain code
        assertEquals(2, TempSun.category(3, 0.0, 100))
        assertEquals(1, TempSun.category(1, 0.0, 60))
        assertEquals(1, TempSun.category(2, 0.0, 25))
    }

    @Test fun extremeWeather() {
        assertEquals(4, TempSun.category(65, 0.0, 100))            // heavy rain code
        assertEquals(4, TempSun.category(95, 0.0, 100))            // thunderstorm
        assertEquals(4, TempSun.category(61, 2.5, 100))            // > 2 mm / 15 min
        assertEquals(4, TempSun.category(3, 0.0, 100, gustsKmh = 65.0))
        assertEquals(4, TempSun.category(1, 0.0, 10, windKmh = 45.0)) // windy but clear: still extreme
        assertEquals(3, TempSun.category(61, 0.3, 100, windKmh = 20.0, gustsKmh = 40.0))
    }
}
