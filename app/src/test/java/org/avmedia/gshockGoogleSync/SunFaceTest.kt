package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.h5600.SunFace
import org.avmedia.gshockapi.model.WatchFaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SunFaceTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val toronto = ZoneId.of("America/Toronto")

    private fun near(expected: LocalDateTime, actual: LocalDateTime?, minutes: Long = 6) =
        assertTrue("expected ~$expected, got $actual",
            actual != null && Duration.between(expected, actual).abs().toMinutes() <= minutes)

    // Reference values (timeanddate.com style almanac figures, rounded to the minute).
    @Test fun lisbonMidNovember() =
        near(LocalDateTime.of(2026, 11, 16, 17, 20), SunFace.sunset(LocalDate.of(2026, 11, 16), 38.7223, -9.1393, lisbon))

    @Test fun lisbonMidsummer() =
        near(LocalDateTime.of(2026, 6, 21, 21, 5), SunFace.sunset(LocalDate.of(2026, 6, 21), 38.7223, -9.1393, lisbon))

    @Test fun torontoMidNovember() =
        near(LocalDateTime.of(2026, 11, 16, 16, 51), SunFace.sunset(LocalDate.of(2026, 11, 16), 43.6532, -79.3832, toronto))

    @Test fun polarNightHasNoSunset() =
        assertEquals(null, SunFace.sunset(LocalDate.of(2026, 12, 21), 78.22, 15.65, ZoneId.of("Arctic/Longyearbyen")))

    @Test fun stepsInTheMorning() = assertEquals(
        WatchFaceItem.STEPS, SunFace.desiredFace(LocalDateTime.of(2026, 11, 16, 10, 0), 38.7223, -9.1393, lisbon))

    @Test fun sunDataFourHoursBeforeSunset() = assertEquals(
        WatchFaceItem.SUNRISE_SUNSET, SunFace.desiredFace(LocalDateTime.of(2026, 11, 16, 13, 30), 38.7223, -9.1393, lisbon))

    @Test fun sunDataUntilTenMinutesAfterSunset() = assertEquals(
        WatchFaceItem.SUNRISE_SUNSET, SunFace.desiredFace(LocalDateTime.of(2026, 11, 16, 17, 25), 38.7223, -9.1393, lisbon))

    @Test fun stepsAfterSunset() = assertEquals(
        WatchFaceItem.STEPS, SunFace.desiredFace(LocalDateTime.of(2026, 11, 16, 17, 40), 38.7223, -9.1393, lisbon))

    @Test fun nextSwitchIsWindowStart() =
        near(LocalDateTime.of(2026, 11, 16, 13, 20), SunFace.nextSwitch(LocalDateTime.of(2026, 11, 16, 9, 0), 38.7223, -9.1393, lisbon))
}
