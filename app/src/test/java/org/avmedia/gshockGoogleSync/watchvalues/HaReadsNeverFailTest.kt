package org.avmedia.gshockGoogleSync.watchvalues

import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extra reads of the Home Assistant push must never throw out or show an error: a failed read is an unknown value. */
class HaReadsNeverFailTest {
    @Test
    fun aReadThatHangsEndsAsUnknownAfterTheTimeout() = runBlocking {
        val registry = VariableRegistry(listOf(Variable("a", "") { kotlinx.coroutines.delay(60_000); 1 }))
        val r = registry.resolve(VariableContext("sync", OffsetDateTime.now(), true), timeoutMs = 50)
        assertEquals(null, r["a"])
    }

    @Test
    fun everyFailingReadLeavesTheValueUnknownAndThrowsNothing() = runBlocking {
        val failures = listOf<Throwable>(
            java.io.IOException("link closed"),
            IllegalStateException("GATT closed"),
            NoClassDefFoundError("x/Y"),
            RuntimeException("timeout"),
        )
        val names = listOf("watch.battery", "health.steps_today", "watch.setting.watch_face")
        for (f in failures) {
            val registry = VariableRegistry(names.map { n -> Variable(n, "", needsWatch = true) { throw f } })
            val resolution = registry.resolveWithFailures(VariableContext("sync", OffsetDateTime.now(), true), names)
            assertTrue(resolution.values.values.all { it == null })
            assertEquals(names.size, resolution.failures.size)
        }
    }
}
