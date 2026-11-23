package org.avmedia.gshockGoogleSync.homeassistant

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Uses Robolectric only for android.text.format.DateFormat; the formatter takes locale and 24 h flag as input. */
@RunWith(RobolectricTestRunner::class)
class ShortDateTimeTest {
    private fun String.plain() = replace('\u202f', ' ').replace('\u00a0', ' ')

    private val zone = ZoneId.of("UTC")
    // 2026-11-14 22:13 UTC
    private val at = java.time.ZonedDateTime.of(2026, 11, 14, 22, 13, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun english24h() = assertEquals("14 Nov, 22:13", ShortDateTime.format(at, Locale.UK, true, zone).plain())

    @Test
    fun english12h() {
        val s = ShortDateTime.format(at, Locale.UK, false, zone).plain().lowercase()
        assertTrue(s, Regex("""14 nov,? 10:13 ?p\.?m\.?""").matches(s))
    }

    @Test
    fun usOrderFollowsLocale() = assertEquals("Nov 14, 22:13", ShortDateTime.format(at, Locale.US, true, zone).plain())

    @Test
    fun noTimeIsBlank() = assertEquals(null, ShortDateTime.formatOrNull(0, Locale.UK, true, zone))
}
