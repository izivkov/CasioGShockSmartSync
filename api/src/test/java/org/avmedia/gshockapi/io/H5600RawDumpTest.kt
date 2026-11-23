package org.avmedia.gshockapi.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class H5600RawDumpTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun capture(seed: Int) =
        H5600Capture(
            notifications = listOf(byteArrayOf(0x05, seed.toByte(), 0x7F), byteArrayOf(0x05, 0x01, 0x02, 0x03)),
            announcements = linkedMapOf(0 to 20, 0x65 to 99),
            stream = ByteArray(5) { (seed + it).toByte() },
        )

    @Test
    fun savesTheStreamAndTheNotificationsInTheRecordingFormat() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        val folder = dump.save(capture(9), nowMillis = 1_000L)

        assertArrayEquals(capture(9).stream, folder.resolve("stream.bin").readBytes())
        val lines = folder.resolve("notifications.txt").readLines().filter { !it.startsWith("#") }
        assertEquals(listOf("A 00 20", "A 65 99", "N 05097f", "N 05010203"), lines)
    }

    @Test
    fun keepsTheLastSevenTransfersNewestFirst() {
        val dump = H5600RawDump(tmp.newFolder("log"), keep = 7)
        for (i in 1..9) dump.save(capture(i), nowMillis = i * 1_000L)

        val kept = dump.transfers()
        assertEquals(7, kept.size)
        assertEquals(9.toByte(), kept.first().resolve("stream.bin").readBytes()[0])
        assertEquals(3.toByte(), kept.last().resolve("stream.bin").readBytes()[0])
    }

    @Test
    fun keepsThirtyDaysAndDropsOlderOnes() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        val day = 24L * 3_600_000
        val t0 = 1_700_000_000_000L
        dump.save(capture(1), nowMillis = t0)
        dump.save(capture(2), nowMillis = t0 + 29 * day)
        assertEquals(2, dump.transfers().size)
        dump.save(capture(3), nowMillis = t0 + 31 * day)
        assertEquals(listOf(3.toByte(), 2.toByte()), dump.transfers().map { it.resolve("stream.bin").readBytes()[0] })
    }

    @Test
    fun manyReadsADayAreAllKeptWithinThirtyDays() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        val t0 = 1_700_000_000_000L
        for (i in 0 until 60) dump.save(capture(i), nowMillis = t0 + i * 2 * 3_600_000L)
        assertEquals(60, dump.transfers().size)
    }

    @Test
    fun aFailedTransferIsKeptMarkedAndShared() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        dump.save(capture(1), nowMillis = 1_000L)
        val failed = dump.save(
            H5600Capture(capture(2).notifications, capture(2).announcements, capture(2).stream, failure = "the watch stopped answering"),
            nowMillis = 2_000L,
        )
        assertTrue(failed.name.endsWith("-failed"))
        assertTrue(failed.resolve("notifications.txt").readLines().any { it == "# FAILED: the watch stopped answering" })
        assertEquals(failed, dump.transfers().first())
        val zip = dump.zipLatest(tmp.newFile("failed.zip"))
        assertNotNull(zip)
        ZipFile(zip).use { assertEquals(setOf("stream.bin", "notifications.txt"), it.entries().asSequence().map { e -> e.name }.toSet()) }
    }

    @Test
    fun theConnectionLogGoesInTheZipWhenThereIsOne() {
        val folder = tmp.newFolder("log")
        val dump = H5600RawDump(folder)
        dump.save(capture(1), nowMillis = 1_000L)
        folder.resolve("connection.log").writeText("2026-11-22 20:00:00.000 49 00 received, 49 01 written\n")
        val zip = dump.zipLatest(tmp.newFile("c.zip"))
        ZipFile(zip).use { z ->
            assertEquals("2026-11-22 20:00:00.000 49 00 received, 49 01 written\n", z.getInputStream(z.getEntry("connection.log")).bufferedReader().readText())
        }
    }

    @Test
    fun twoTransfersInTheSameMillisecondDoNotOverwrite() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        dump.save(capture(1), nowMillis = 5L)
        dump.save(capture(2), nowMillis = 5L)

        assertEquals(2, dump.transfers().size)
    }

    @Test
    fun zipHoldsTheNewestTransferOnly() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        dump.save(capture(1), nowMillis = 1_000L)
        dump.save(capture(2), nowMillis = 2_000L)

        val zip = dump.zipLatest(tmp.newFile("out.zip"))

        assertNotNull(zip)
        ZipFile(zip!!).use {
            assertEquals(setOf("stream.bin", "notifications.txt"), it.entries().asSequence().map { e -> e.name }.toSet())
            assertEquals(2.toByte(), it.getInputStream(it.getEntry("stream.bin")).readBytes()[0])
        }
    }

    @Test
    fun onlyTheConnectionLogStillMakesAZip() {
        val folder = tmp.newFolder("only-log")
        folder.resolve("connection.log").writeText("2026-11-22 20:00:00.000 49 00 received\n")
        val zip = H5600RawDump(folder).zipLatest(tmp.newFile("only.zip"))
        assertNotNull(zip)
        ZipFile(zip!!).use { assertEquals(setOf("connection.log"), it.entries().asSequence().map { e -> e.name }.toSet()) }
    }

    @Test
    fun noTransfersMeansNoZip() {
        val dump = H5600RawDump(tmp.newFolder("log"))
        assertNull(dump.zipLatest(tmp.newFile("none.zip")))
        assertTrue(dump.transfers().isEmpty())
    }

    @Test
    fun aFolderThatCannotBeWrittenFailsWithoutTouchingTheTransfer() {
        val notADir = tmp.newFile("file")
        val dump = H5600RawDump(notADir.resolve("log"))
        val result = runCatching { dump.save(capture(1), 1L) }
        assertTrue(result.isFailure)
    }
}
