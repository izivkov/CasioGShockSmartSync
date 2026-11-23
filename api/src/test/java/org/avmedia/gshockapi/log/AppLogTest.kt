package org.avmedia.gshockapi.log

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var file: File

    @Before fun install() {
        file = File(tmp.root, "connection.log")
        AppLog.logFile = file
        AppLog.clock = { 1_700_000_000_000L }
        AppLog.maxFileBytes = 1024L * 1024L
        AppLog.clear()
    }

    @After fun restore() {
        AppLog.logFile = null
        AppLog.clock = System::currentTimeMillis
        AppLog.maxFileBytes = 1024L * 1024L
    }

    @Test fun lineHasTimeSourceAndText() {
        AppLog.log(LogSource.Webhook, "Webhook 'x' (example.com): failed after 4 tries: timeout")
        val parsed = AppLog.parse(AppLog.readLines().single())
        assertEquals(LogSource.Webhook, parsed.source)
        assertTrue(parsed.time.startsWith("2023-11-14 "))
        assertEquals("Webhook 'x' (example.com): failed after 4 tries: timeout", parsed.text)
    }

    @Test fun oneLineEvenWithNewlines() {
        AppLog.log(LogSource.App, "a\nb")
        assertEquals(1, AppLog.readLines().size)
    }

    @Test fun failureNamesTheClassNotTheMessage() {
        AppLog.failure(LogSource.Health, "Saving failed", IllegalStateException("token=SECRET"))
        val line = AppLog.readLines().single()
        assertTrue(line, line.endsWith("Saving failed (IllegalStateException)"))
        assertFalse(line.contains("SECRET"))
    }

    @Test fun theLogIsBoundedAndTheOldestIsDropped() {
        AppLog.maxFileBytes = 2_000L
        for (i in 1..200) AppLog.log(LogSource.Watch, "line %04d ".format(i) + "x".repeat(40))
        val lines = AppLog.readLines()
        assertTrue("files stay near the cap", file.length() <= 2_000L && AppLog.rolledFile(file).length() <= 2_000L)
        assertTrue(lines.size < 200)
        assertTrue(lines.last().contains("line 0200"))
        assertFalse(lines.any { it.contains("line 0001 ") })
        // Older lines come before newer ones.
        assertEquals(lines.sorted(), lines)
    }

    @Test fun clearRemovesBothFiles() {
        AppLog.maxFileBytes = 300L
        repeat(20) { AppLog.log(LogSource.App, "x".repeat(50)) }
        assertTrue(AppLog.rolledFile(file).isFile)
        AppLog.clear()
        assertFalse(file.exists() || AppLog.rolledFile(file).exists())
        assertTrue(AppLog.readLines().isEmpty())
    }
}
