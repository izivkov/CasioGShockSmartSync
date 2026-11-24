package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watchvalues.RawWatchRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchSettingsAccess
import org.avmedia.gshockapi.io.RawSettingIO
import org.avmedia.gshockapi.io.SettingLink
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.gshockapi.io.WatchExchange
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * One request/answer path and one writer: the app's screens, Home Assistant and the library's own settings calls all
 * read and write through [RawSettingIO] (moved from the old RawWatchRequestTest: the answer text, the busy rule).
 */
class SingleSettingsPathTest {
    private class FakeLink : SettingLink {
        var value = byteArrayOf(0x51, 1, 2, 2, 2, 2)
        var keeps = true
        val requests = ArrayList<List<Int>>()
        val checked = ArrayList<List<Int>>()

        override fun sendRequest(bytes: ByteArray) {
            requests += bytes.map { it.toInt() and 0xFF }
            if (bytes[0] == value[0]) RawSettingIO.offer(value.joinToString(" ", "0x") { "%02X".format(it) })
        }

        override fun sendPacket(packet: IntArray) = Unit

        override suspend fun sendChecked(bytes: ByteArray): Boolean {
            checked += bytes.map { it.toInt() and 0xFF }
            if (keeps) value = bytes.copyOf()
            return true
        }
    }

    private val link = FakeLink()

    @Before fun install() {
        RawSettingIO.link = link
        RawSettingIO.settleMs = 0
    }

    @After fun release() {
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
        RawSettingIO.settleMs = 1_000L
    }

    @Test fun theRawCacheGetsTheAnswerAsText() = runBlocking {
        assertEquals(listOf("0x51 01 02 02 02 02"), RawWatchRequest().request(0x51, 1_000))
    }

    @Test fun noAnswerIsNothing() = runBlocking {
        assertEquals(emptyList<String>(), RawWatchRequest().request(0x41, 200))
    }

    @Test fun noRawReadStartsWhileATransferHoldsTheLink() = runBlocking {
        WatchExchange.mutex.lock()
        assertEquals(emptyList<String>(), RawWatchRequest().request(0x51, 1_000))
        assertTrue("nothing may be written during a transfer", link.requests.isEmpty())
    }

    @Test fun aFaceWriteFromTheScreensWriterIsByteExactAndReadBack() = runBlocking {
        val result = WatchSettingsAccess().update(0x51, setOf(1), { f -> f.copyOf().also { it[1] = 3 } })
        assertEquals(SettingWriteResult.Done, result)
        assertEquals(listOf(listOf(0x51, 3, 2, 2, 2, 2)), link.checked)
        assertEquals(2, link.requests.size)
        assertArrayEquals(byteArrayOf(0x51, 3, 2, 2, 2, 2), link.value)
    }

    @Test fun aFaceThatTheWatchDoesNotKeepIsRefused() = runBlocking {
        link.keeps = false
        val result = WatchSettingsAccess().update(0x51, setOf(1), { f -> f.copyOf().also { it[1] = 3 } })
        assertTrue(result is SettingWriteResult.Failed)
        assertEquals(1, link.checked.size)
    }

    private fun sources(vararg roots: String): List<File> =
        roots.flatMap { r -> File(r).walkTopDown().filter { it.extension == "kt" }.toList() }
            .also { assertTrue("sources not found from ${File(".").absolutePath}", it.isNotEmpty()) }

    private fun offenders(files: List<File>, needle: String, allowed: Set<String>): List<String> =
        files.filter { it.name !in allowed }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (needle in line) "${f.path}:${i + 1}" else null }
        }

    @Test fun onlyTheOneIoBuildsTheCheckedWriterAndWritesChecked() {
        val all = sources("src/main/java", "../api/src/main/java")
        assertEquals(emptyList<String>(), offenders(all, "SafeSettingWrite(", setOf("RawSettingIO.kt", "SafeSettingWrite.kt")))
        assertEquals(emptyList<String>(), offenders(all, "writeChecked(GetSetMode.SET", setOf("RawSettingIO.kt")))
    }

    @Test fun theFaceScreenAndHomeAssistantWriteThroughTheSameAccess() {
        val face = File("src/main/java/org/avmedia/gshockGoogleSync/ui/watchhealth/WatchFaceViewModel.kt").readText()
        val ha = File("src/main/java/org/avmedia/gshockGoogleSync/homeassistant/HaSync.kt").readText()
        assertTrue("the face screen writes through access.update", "access.update(WatchFaceCodec.CODE" in face)
        assertTrue("Home Assistant commands write through access.update", "access.update(s.code" in ha)
        assertTrue("the registry's watch_face entry names the face code", "pick(\"watch_face\"" in File("src/main/java/org/avmedia/gshockGoogleSync/homeassistant/WatchSettingRegistry.kt").readText())
    }
}
