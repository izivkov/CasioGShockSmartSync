package org.avmedia.gshockGoogleSync.watchvalues

import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.avmedia.smartsync.homeassistant.HaSensors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extra reads of the Home Assistant push must never throw out or show an error: a failed read is an unknown value. */
class HaReadsNeverFailTest {
    @Test
    fun everyFailingReadLeavesTheValueUnknownAndThrowsNothing() = runBlocking {
        val failures = listOf<Throwable>(
            java.io.IOException("link closed"),
            IllegalStateException("GATT closed"),
            NoClassDefFoundError("x/Y"),
            kotlinx.coroutines.TimeoutCancellationException::class.java.let { RuntimeException("timeout") },
        )
        for (f in failures) {
            val registry = VariableRegistry(HaSensors.VARIABLES.map { n -> Variable(n, "", needsWatch = true) { throw f } })
            val resolution = registry.resolveWithFailures(VariableContext("sync", OffsetDateTime.now(), true), HaSensors.VARIABLES)
            assertTrue(resolution.values.values.all { it == null })
            assertTrue(HaSensors.states(resolution.values).isEmpty())
            assertEquals(HaSensors.VARIABLES.size, resolution.failures.size)
        }
    }

    @Test
    fun aReadThatHangsEndsAsUnknownAfterTheTimeout() = runBlocking {
        val registry = VariableRegistry(listOf(Variable("a", "") { kotlinx.coroutines.delay(60_000); 1 }))
        val r = registry.resolve(VariableContext("sync", OffsetDateTime.now(), true), timeoutMs = 50)
        assertEquals(null, r["a"])
    }
}
