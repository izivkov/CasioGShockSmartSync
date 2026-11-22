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
        val dump = H5600RawDump(tmp.newFolder("log"))
        for (i in 1..9) dump.save(capture(i), nowMillis = i * 1_000L)

        val kept = dump.transfers()
        assertEquals(7, kept.size)
        assertEquals(9.toByte(), kept.first().resolve("stream.bin").readBytes()[0])
        assertEquals(3.toByte(), kept.last().resolve("stream.bin").readBytes()[0])
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
