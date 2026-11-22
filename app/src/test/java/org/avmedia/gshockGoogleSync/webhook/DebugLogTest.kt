package org.avmedia.gshockGoogleSync.webhook

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {
    @After
    fun reset() = DebugLog.clear()

    @Test
    fun keepsTheNewestLines() {
        repeat(DebugLog.MAX_LINES + 5) { DebugLog.add("line $it") }
        val lines = DebugLog.snapshot()
        assertEquals(DebugLog.MAX_LINES, lines.size)
        assertEquals("line 5", lines.first())
        assertEquals("line ${DebugLog.MAX_LINES + 4}", lines.last())
    }

    @Test
    fun tokensNeverLeaveThePhone() {
        // The log is posted to a webhook; a header or URL token in a log line must not go with it.
        DebugLog.add("send headers Authorization: Bearer abc.DEF-123 to host")
        DebugLog.add("POST https://x.example/hook?token=s3cret&x=1")
        val all = DebugLog.snapshot().joinToString("\n")
        assertFalse(all, "abc.DEF-123" in all)
        assertFalse(all, "s3cret" in all)
        assertTrue(all, "Bearer" in all)
    }
}
