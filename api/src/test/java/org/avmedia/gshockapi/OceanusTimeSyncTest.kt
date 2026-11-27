package org.avmedia.gshockapi

import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.protocols.OceanusLink
import org.avmedia.gshockapi.protocols.OceanusSyncException
import org.avmedia.gshockapi.protocols.OceanusTimeSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class OceanusTimeSyncTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02x".format(it) }

    /** Answers reads from a script and records every read and write, in order. */
    private class FakeLink(
        replies: Map<String, List<String>>,
        private val failingWrite: String? = null,
    ) : OceanusLink {
        private val queues = replies.mapValues { ArrayDeque(it.value) }
        val trace = mutableListOf<String>()
        val writes = mutableListOf<ByteArray>()

        private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02x".format(it) }

        override suspend fun read(request: ByteArray): ByteArray {
            trace += "R ${hex(request)}"
            val reply = queues[hex(request)]?.removeFirstOrNull() ?: throw OceanusSyncException("no reply")
            return reply.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        }

        override suspend fun write(command: ByteArray) {
            trace += "W ${hex(command)}"
            if (failingWrite != null && hex(command).startsWith(failingWrite)) throw OceanusSyncException("write failed")
            writes += command
        }
    }

    // Replies shaped like the watch's (capture of a manual time sync), with a placeholder
    // home city (1234).
    private val replies =
        mapOf(
            "1d" to listOf("1d 00 01 06 02 34 12 00 00 ff ff ff ff ff ff"),
            "1e" to listOf("1e 00 34 12 ec 04 01", "1e 01 00 00 00 00 00"),
            "11" to listOf("11 0f 0f 0f 06 00 50 00 04 00 01 00 00 1e 03"),
        )

    // America/New_York in November: UTC-5 (-20 x 15 min), +1 h DST (4), US rule 1, no DST now.
    private val newYork = OceanusTimeSync.HomeZone(offset = -20, dstShift = 4, rule = 1, inDst = false)

    // Pacific/Easter in November: UTC-6, +1 h DST in effect now, rule 0x1C.
    private val easterIsland = OceanusTimeSync.HomeZone(offset = -24, dstShift = 4, rule = 0x1C, inDst = true)

    private val syncTime = LocalDateTime.of(2026, 11, 16, 21, 56, 47, 4_000_000) // a Monday

    @Test
    fun readsTheDstRecordsThenWritesThemAndTheTime() {
        val link = FakeLink(replies)
        runBlocking { OceanusTimeSync.run(link, newYork) { syncTime } }

        // Values as the official app writes them for New York (capture C02), city kept.
        val expected =
            listOf(
                "R 1d",
                "R 1e",
                "R 1e",
                "W 1d 00 01 06 02 34 12 00 00 ff ff ff ff ff ff",
                "W 1e 00 34 12 ec 04 01",
                "W 1e 01 00 00 00 00 00",
                "W 09 ea 07 0b 10 15 38 2f 01 01 01",
            )
        assertEquals(expected, link.trace)
    }

    @Test
    fun stopsBeforeWritingWhenAReplyIsUnexpected() {
        val shortDstState = replies + ("1d" to listOf("1d 00 01 06"))
        val link = FakeLink(shortDstState)
        try {
            runBlocking { OceanusTimeSync.run(link, newYork) { syncTime } }
            fail("expected OceanusSyncException")
        } catch (expected: OceanusSyncException) {
            // stopped
        }
        assertTrue(link.writes.isEmpty())
    }

    @Test
    fun stopsWhenDstSettingSlotsArriveOutOfOrder() {
        val swapped = replies + ("1e" to listOf("1e 01 00 00 00 00 00", "1e 00 34 12 ec 04 01"))
        val link = FakeLink(swapped)
        try {
            runBlocking { OceanusTimeSync.run(link, newYork) { syncTime } }
            fail("expected OceanusSyncException")
        } catch (expected: OceanusSyncException) {
            // stopped
        }
        assertTrue(link.writes.isEmpty())
    }

    @Test
    fun stopsAfterAFailedWrite() {
        val link = FakeLink(replies, failingWrite = "1d")
        try {
            runBlocking { OceanusTimeSync.run(link, newYork) { syncTime } }
            fail("expected OceanusSyncException")
        } catch (expected: OceanusSyncException) {
            // stopped
        }
        // The failed DST state write is the last thing attempted: no DST setting, no time.
        assertTrue(link.trace.last().startsWith("W 1d"))
        assertTrue(link.writes.isEmpty())
    }

    @Test
    fun followsThePhoneTimeZoneKeepingTheHomeCity() {
        // As the official app wrote for Pacific/Easter (capture C07), except that the
        // home city (here 1234) is kept instead of being replaced by Casio's city number.
        val link = FakeLink(replies)
        runBlocking { OceanusTimeSync.run(link, easterIsland) { syncTime } }
        val writes = link.trace.filter { it.startsWith("W 1d") || it.startsWith("W 1e") }
        assertEquals(
            listOf(
                "W 1d 00 01 07 02 34 12 00 00 ff ff ff ff ff ff",
                "W 1e 00 34 12 e8 04 1c",
                "W 1e 01 00 00 00 00 00",
            ),
            writes,
        )
    }

    @Test
    fun manualSummerTimeIsKept() {
        val on = OceanusTimeSync.encodeDstWatchState(bytesOf("1d 00 01 01 02 34 12 00 00 ff ff ff ff ff ff"), newYork)
        assertEquals("1d 00 01 01 02 34 12 00 00 ff ff ff ff ff ff", hex(on))
        val off = OceanusTimeSync.encodeDstWatchState(bytesOf("1d 00 01 00 02 34 12 00 00 ff ff ff ff ff ff"), easterIsland)
        assertEquals("1d 00 01 00 02 34 12 00 00 ff ff ff ff ff ff", hex(off))
        // Automatic, back from a zone in DST: the DST bit is cleared.
        val auto = OceanusTimeSync.encodeDstWatchState(bytesOf("1d 00 01 07 02 34 12 00 00 ff ff ff ff ff ff"), newYork)
        assertEquals("1d 00 01 06 02 34 12 00 00 ff ff ff ff ff ff", hex(auto))
    }

    @Test
    fun zoneWithoutDstKeepsTheShiftAndClearsTheRule() {
        val tokyo = OceanusTimeSync.HomeZone(offset = 36, dstShift = 0, rule = 0, inDst = false)
        val written = OceanusTimeSync.encodeHomeDstSetting(bytesOf("1e 00 34 12 ec 04 01"), tokyo)
        assertEquals("1e 00 34 12 24 04 00", hex(written))
    }

    @Test
    fun homeZoneFromTheTimeZoneRules() {
        val november = Instant.parse("2026-11-22T04:00:00Z")
        val july = Instant.parse("2026-07-01T12:00:00Z")
        assertEquals(newYork, OceanusTimeSync.homeZone(ZoneId.of("America/New_York"), 1, november))
        assertEquals(newYork.copy(inDst = true), OceanusTimeSync.homeZone(ZoneId.of("America/New_York"), 1, july))
        assertEquals(easterIsland, OceanusTimeSync.homeZone(ZoneId.of("Pacific/Easter"), 0x1C, november))
        assertEquals(
            OceanusTimeSync.HomeZone(offset = 36, dstShift = 0, rule = 0, inDst = false),
            OceanusTimeSync.homeZone(ZoneId.of("Asia/Tokyo"), 0, november),
        )
    }

    @Test
    fun pairingWritesAppInfoAndBleSettingsThenSetsTheTime() {
        val link = FakeLink(replies)
        runBlocking { OceanusTimeSync.run(link, newYork, pairing = true) { syncTime } }
        assertEquals(
            listOf(
                "W 22 95 00 00 00 00 00 00 00 00 00 02",
                "R 11",
                "W 11 0f 0f 0f 06 00 50 00 04 00 01 00 00 1e 03",
                "R 1d",
            ),
            link.trace.take(4),
        )
        assertEquals("W 09 ea 07 0b 10 15 38 2f 01 01 01", link.trace.last())
    }

    @Test
    fun pairingStopsIfBleSettingsCannotBeRead() {
        val link = FakeLink(replies + ("11" to listOf("11 0f")))
        try {
            runBlocking { OceanusTimeSync.run(link, newYork, pairing = true) { syncTime } }
            fail("expected OceanusSyncException")
        } catch (expected: OceanusSyncException) {
            // stopped
        }
        assertEquals(listOf("W 22"), link.trace.filter { it.startsWith("W") }.map { it.take(4) })
    }

    private fun bytesOf(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun encodesCurrentTimeLikeTheOfficialApp() {
        // 2026-11-17 00:30:36 Tuesday, fraction 0xdd/256 s
        val time = LocalDateTime.of(2026, 11, 17, 0, 30, 36, 863_281_250)
        assertEquals("09 ea 07 0b 11 00 1e 24 02 dd 01", hex(OceanusTimeSync.encodeCurrentTime(time)))
    }

    @Test
    fun sundayIsDaySeven() {
        val sunday = LocalDateTime.of(2026, 11, 22, 12, 0, 0)
        assertEquals(7, OceanusTimeSync.encodeCurrentTime(sunday)[8].toInt())
    }
}
