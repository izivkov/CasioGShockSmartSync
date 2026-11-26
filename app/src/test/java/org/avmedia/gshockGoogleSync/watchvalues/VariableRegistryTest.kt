package org.avmedia.gshockGoogleSync.watchvalues

import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableRegistryTest {
    private fun ctx(connected: Boolean = true) = VariableContext("test", OffsetDateTime.now(), connected)

    @Test
    fun aThrowingProviderGivesNullAndOthersStillRun() = runBlocking {
        val registry = VariableRegistry(
            listOf(
                Variable("a", "boom") { error("watch does not support this") },
                Variable("b", "ok") { 7 },
            )
        )
        val values = registry.resolve(ctx())
        assertNull(values["a"])
        assertEquals(7, values["b"])
    }

    @Test
    fun aSlowProviderTimesOutToNull() = runBlocking {
        val registry = VariableRegistry(listOf(Variable("slow", "never answers") { delay(10_000); 1 }))
        assertNull(registry.resolve(ctx(), timeoutMs = 50)["slow"])
    }

    @Test
    fun watchVariablesAreNullWithoutConnectionAndNotCalled() = runBlocking {
        var called = false
        val registry = VariableRegistry(
            listOf(Variable("watch.x", "needs watch", needsWatch = true) { called = true; 1 })
        )
        assertNull(registry.resolve(ctx(connected = false))["watch.x"])
        assertTrue(!called)
    }

    @Test
    fun onlyWantedVariablesAreRead() = runBlocking {
        var readB = false
        val registry = VariableRegistry(
            listOf(Variable("a", "") { 1 }, Variable("b", "") { readB = true; 2 })
        )
        val values = registry.resolve(ctx(), wanted = setOf("a"))
        assertEquals(setOf("a"), values.keys)
        assertTrue(!readB)
    }

    @Test
    fun cachedSharesOneReadBetweenVariables() = runBlocking {
        var reads = 0
        val registry = VariableRegistry(
            listOf(
                Variable("s.one", "") { it.cached("s") { reads++; "x" } },
                Variable("s.two", "") { it.cached("s") { reads++; "y" } },
            )
        )
        registry.resolve(ctx())
        assertEquals(1, reads)
    }

    @Test
    fun resolveReadsOnlyWhatIsWantedAndSkipsWatchValuesWhileDisconnected() = runBlocking {
        var other = 0
        val registry = VariableRegistry(
            listOf(
                Variable("event", "") { it.event },
                Variable("watch.battery", "", needsWatch = true) { 50 },
                Variable("other", "") { other++; 1 },
            )
        )
        val v = registry.resolve(ctx(connected = false), setOf("event", "watch.battery"))
        assertEquals(mapOf("event" to "test", "watch.battery" to null), v)
        assertEquals(0, other)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateNamesAreRejected() {
        VariableRegistry(listOf(Variable("a", "") { 1 }, Variable("a", "") { 2 }))
    }
}

class VariableRegistryErrorTest {
    private fun ctx() = VariableContext("test", OffsetDateTime.now(), true)

    @Test
    fun aProviderThrowingAnErrorNotAnExceptionDoesNotStopTheOthers() = runBlocking {
        val registry = VariableRegistry(
            listOf(
                Variable("a", "class missing on the device") { throw NoClassDefFoundError("org/example/Missing") },
                Variable("b", "fine") { 7 },
            )
        )
        assertEquals(mapOf("a" to null, "b" to 7), registry.resolve(ctx()))
    }
}

class DiagnosticsTest {
    @Test
    fun oneLineIsClassAndMessage() {
        assertEquals("NoClassDefFoundError: org/example/Gone", Diagnostics.oneLine(NoClassDefFoundError("org/example/Gone")))
        assertEquals("IllegalStateException", Diagnostics.oneLine(IllegalStateException()))
    }

    @Test
    fun failedProvidersAreListedWithTheReason() = runBlocking {
        val registry = VariableRegistry(listOf(Variable("a", "") { throw NoClassDefFoundError("org/example/Gone") }))
        val r = registry.resolveWithFailures(VariableContext("test", OffsetDateTime.now(), true))
        assertEquals(mapOf("a" to "NoClassDefFoundError: org/example/Gone"), r.failures)
    }
}
