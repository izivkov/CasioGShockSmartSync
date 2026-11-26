package org.avmedia.gshockGoogleSync.log

import java.io.File
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

/** The App log: every line through Timber with its source as tag; the screen and Share read the app's own logcat. */
class AppLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val seen: MutableList<Triple<Int, String?, String>> = Collections.synchronizedList(mutableListOf())
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) { seen += Triple(priority, tag, message) }
    }

    @Before fun install() {
        AppLog.clock = { 1_700_000_000_000L }
        AppLog.clear()
        AppLog.clearedAt = 0
        Timber.plant(tree)
    }

    @After fun restore() {
        Timber.uproot(tree)
        org.avmedia.gshockapi.log.LibraryLog.sink = org.avmedia.gshockapi.log.LibraryLog.TIMBER
        AppLog.clock = System::currentTimeMillis
    }

    @Test fun aLineGoesToTimberTaggedWithItsSource() {
        AppLog.log(LogSource.HomeAssistant, "push sent")
        assertEquals(Triple(android.util.Log.INFO, "Home Assistant", "push sent"), seen.single())
    }

    @Test fun oneLineEvenWithNewlines() {
        AppLog.log(LogSource.App, "a\nb")
        assertEquals("a b", seen.single().third)
    }

    @Test fun failureNamesTheClassNotTheMessage() {
        AppLog.failure(LogSource.Health, "LifeLog read failed", IllegalStateException("https://secret.example/?token=x"))
        val (priority, tag, text) = seen.single()
        assertEquals(android.util.Log.WARN, priority)
        assertEquals("Health", tag)
        assertEquals("LifeLog read failed (IllegalStateException)", text)
    }

    @Test fun repeatedLinesCollapse() {
        repeat(5) { AppLog.log(LogSource.Watch, "link lost") }
        AppLog.log(LogSource.Watch, "link up")
        assertEquals(listOf("link lost", "(the line before repeated 4 times)", "link up"), seen.map { it.third })
    }

    @Test fun libraryLinesKeepTheirSource() {
        AppLog.install()
        org.avmedia.gshockapi.log.LibraryLog.log(org.avmedia.gshockapi.log.LibraryLog.Source.Workouts, "Workout read: 1C the watch is ready")
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("49 00 received, 49 01 written")
        assertEquals(listOf("Workouts", "Watch"), seen.map { it.second })
        assertEquals("49 00 received, 49 01 written", seen.last().third)
    }

    @Test fun theLogcatCommandReadsOnlyOurLinesWithDates() {
        assertEquals(listOf("logcat", "-d", "-v", "threadtime", "-v", "year", "--uid=10234"), Logcat.command(sdk = 34, uid = 10234, pid = 999))
        assertEquals("before Android 9 there is no --uid", listOf("logcat", "-d", "-v", "threadtime", "-v", "year", "--pid=999"), Logcat.command(sdk = 26, uid = 10234, pid = 999))
    }

    @Test fun aLogcatLineBecomesSourceAndText() {
        val l = Logcat.parse("2026-11-24 12:00:00.123  1234  1250 I Home Assistant: push sent: 3 sensors")!!
        assertEquals("2026-11-24 12:00:00.123", l.time)
        assertEquals(LogSource.HomeAssistant, l.source)
        assertEquals("push sent: 3 sensors", l.text)
        assertEquals("a plugin's id is its source", LogSource.HomeAssistant, Logcat.parse("2026-11-24 12:00:00.123  1234  1250 W home_assistant_mqtt: x")!!.source)
        assertEquals(LogSource.Watch, Logcat.parse("2026-11-24 12:00:00.123  1234  1250 I Watch   : 49 00 received")!!.source)
        assertNull("another tag is no source", Logcat.parse("2026-11-24 12:00:00.123  1234  1250 D OpenGLRenderer: x")!!.source)
        assertNull("a header line is no line", Logcat.parse("--------- beginning of main"))
    }

    @Test fun theScreenShowsTheLastLinesInMemoryWhenLogcatGivesNothing() {
        AppLog.log(LogSource.Watch, "Connection kind 14: setup starts")
        val model = org.avmedia.gshockGoogleSync.ui.settings.AppLogModel { AppLog.recentLines() }
        model.load()
        assertEquals("Connection kind 14: setup starts", model.page(null, true, 10).single().line.text)
    }

    @Test fun shareZipsAHeaderAndTheLines() {
        val header = linkedMapOf("App version" to "42.9.30", "Watch model" to "CASIO GBX-H5600", "Android" to "15 (API 35)")
        val zip = Logcat.zip(File(tmp.root, "share.zip"), header, listOf("line 1", "line 2"))!!
        java.util.zip.ZipFile(zip).use { z ->
            assertEquals(listOf("header.txt", "logcat.txt"), z.entries().toList().map { it.name })
            val head = z.getInputStream(z.getEntry("header.txt")).bufferedReader().readText()
            assertTrue(head, head.contains("App version: 42.9.30") && head.contains("Watch model: CASIO GBX-H5600") && head.contains("Android: 15 (API 35)"))
            assertEquals("line 1\nline 2\n", z.getInputStream(z.getEntry("logcat.txt")).bufferedReader().readText())
        }
    }

    @Test fun nothingLoggedSharesNothing() {
        assertNull(Logcat.zip(File(tmp.root, "share.zip"), emptyMap(), emptyList()))
        assertFalse(File(tmp.root, "share.zip").exists())
    }

    @Test fun clearHidesWhatWasLoggedBefore() {
        val line = "2023-11-14 22:13:20.000  1  1 I Watch: old"
        AppLog.clock = { 1_800_000_000_000L }
        AppLog.clear()
        val model = org.avmedia.gshockGoogleSync.ui.settings.AppLogModel { listOf(line) }
        model.load()
        assertTrue(model.page(null, true, 10).isEmpty())
    }
}
