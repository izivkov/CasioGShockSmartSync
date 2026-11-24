package org.avmedia.gshockGoogleSync.ui.settings

import org.avmedia.gshockGoogleSync.log.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The App log screen with a full log (2 x 1 MB): one read and parse, then pages and filters on the parsed lines. */
class AppLogModelTest {
    private fun fullLog(): List<String> {
        val out = ArrayList<String>()
        var bytes = 0
        var i = 0
        while (bytes < 2 * 1024 * 1024) {
            val line = when (i % 10) {
                in 0..6 -> "2026-11-23 17:16:58.414 [Watch] rx +3s 002d: 05 9A 9C FF FF FF A4 01 00 00 00 01 01 00 01 00 9F FF FF FF FF 9D $i"
                7 -> "2026-11-23 17:17:00.000 [Watch] read 0x28 waits, rx 28 06 1E 00 00 FA 00 00 00 00 $i"
                8 -> "2026-11-23 17:17:01.000 [Home Assistant] Home Assistant update sensors: sent $i"
                else -> "2026-11-23 17:17:02.000 [App] Session ready, Home sent $i"
            }
            out += line; bytes += line.length + 1; i++
        }
        return out
    }

    @Test fun aFullLogShowsItsFirstPageQuickly() {
        val raw = fullLog()
        val model = AppLogModel { raw }
        val t0 = System.nanoTime()
        model.load()
        val first = model.page(null, showRaw = false, limit = AppLogModel.PAGE)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(AppLogModel.PAGE, first.size)
        assertTrue("first page took $ms ms", ms < 3_000)
        assertEquals(raw.size - 1, first.first().id)
        assertTrue(first.first().line.text.endsWith(" ${raw.size - 1}") || first.first().id > first.last().id)
    }

    @Test fun rawFramesAreHiddenUnlessAskedFor() {
        val model = AppLogModel { fullLog() }.apply { load() }
        assertFalse(model.page(null, showRaw = false, limit = 2_000).any { it.rawFrame })
        assertTrue(model.page(null, showRaw = true, limit = 2_000).any { it.rawFrame })
        assertTrue(model.page(LogSource.Watch, showRaw = false, limit = 2_000).all { it.line.text.startsWith("read 0x28") })
    }

    @Test fun filtersAndPagesDoNotReadTheFileAgain() {
        var reads = 0
        val raw = fullLog()
        val model = AppLogModel { reads++; raw }
        model.load()
        model.page(null, false, AppLogModel.PAGE)
        model.page(LogSource.App, false, AppLogModel.PAGE)
        model.page(LogSource.HomeAssistant, true, 2 * AppLogModel.PAGE)
        assertEquals(1, reads)
    }

    @Test fun idsAreStableAndNewestFirst() {
        val model = AppLogModel { listOf("2026-11-23 10:00:00.000 [App] a", "2026-11-23 10:00:01.000 [App] b") }.apply { load() }
        val p = model.page(null, true, 10)
        assertEquals(listOf(1, 0), p.map { it.id })
        assertEquals(listOf("b", "a"), p.map { it.line.text })
    }
}
