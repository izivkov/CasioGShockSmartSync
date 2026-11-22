package org.avmedia.gshockGoogleSync.webhook

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSettingsReaderTest {
    private class Fake(val answers: (Int) -> List<String>) : RawRequester {
        val asked = mutableListOf<Int>()
        override suspend fun request(code: Int, timeoutMs: Long): List<String> {
            asked += code
            return answers(code)
        }
    }

    private fun ok(code: Int) = listOf("0x%02X 01 02".format(code))

    @Test
    fun theTableHoldsTheAgreedCodesOnceEach() {
        val expected = listOf(
            0x51, 0x52, 0x41, 0x50, 0x82, 0x4F, 0x4A, 0x38, 0x59, 0x4E, 0x54,
            0x55, 0x57, 0x58, 0x40, 0x44, 0x2A, 0x2E, 0x45, 0x43, 0x13,
        )
        assertEquals(expected, RawSettings.CODES)
        assertEquals(expected.size, RawSettings.CODES.toSet().size)
        assertEquals("0x2A", RawSettings.name(0x2A))
        assertEquals("watch.raw.0x13", RawSettings.variable(0x13))
    }

    @Test
    fun readsEveryCodeOneAfterTheOtherAndKeepsHex() = runBlocking {
        val fake = Fake { ok(it) }
        val reader = RawSettingsReader(clock = { 1000L })
        reader.refresh(fake)

        assertEquals(RawSettings.CODES, fake.asked)
        assertEquals("41 01 02", reader.values()["0x41"])
        assertEquals(RawSettings.CODES.size, reader.values().size)
        assertEquals(1000L, reader.lastReadMillis())
    }

    @Test
    fun aCodeIsReadAtMostOnceInThirtyMinutes() = runBlocking {
        var now = 0L
        val fake = Fake { ok(it) }
        val reader = RawSettingsReader(clock = { now })
        reader.refresh(fake)
        val first = fake.asked.size
        assertTrue(first > 0)
        now = 29 * 60_000L
        reader.refresh(fake)
        assertEquals(first, fake.asked.size)
        now = 30 * 60_000L
        reader.refresh(fake)
        assertEquals(2 * first, fake.asked.size)
    }

    @Test
    fun nothingIsReadWhileTheLifeLogRuns() = runBlocking {
        val fake = Fake { ok(it) }
        val reader = RawSettingsReader(busy = { true })
        reader.refresh(fake)

        assertTrue(fake.asked.isEmpty())
        assertTrue(reader.values().isEmpty())
        assertNull(reader.lastReadMillis())
    }

    @Test
    fun aFailingOrEmptyCodeStaysUnknownAndTheOthersAreStillRead() = runBlocking {
        val fake = Fake { code ->
            when (code) {
                0x52 -> throw IllegalStateException("no link")
                0x41 -> emptyList()
                0x50 -> listOf("0x99 01") // answer to another request
                else -> ok(code)
            }
        }
        val reader = RawSettingsReader()
        reader.refresh(fake)

        assertNull(reader.values()["0x52"])
        assertNull(reader.values()["0x41"])
        assertNull(reader.values()["0x50"])
        assertEquals("51 01 02", reader.values()["0x51"])
        assertEquals(RawSettings.CODES.size - 3, reader.values().size)
    }
}
