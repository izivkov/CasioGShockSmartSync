package org.avmedia.gshockGoogleSync.webhook

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
    fun renderReadsOnlyWhatTheTemplateUsesAndReportsMissing() = runBlocking {
        val registry = VariableRegistry(
            listOf(
                Variable("event", "") { it.event },
                Variable("watch.battery", "", needsWatch = true) { 50 },
            )
        )
        val r = registry.render("""{"e": {{event}}, "b": {{watch.battery}}, "u": {{zzz}}}""", true, ctx(connected = false))
        assertEquals("""{"e": "test", "b": null, "u": null}""", r.text)
        assertEquals(listOf("zzz"), r.unknown)
        assertEquals(listOf("watch.battery"), r.missing)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateNamesAreRejected() {
        VariableRegistry(listOf(Variable("a", "") { 1 }, Variable("a", "") { 2 }))
    }
}

class VariableRegistryErrorTest {
    private fun ctx() = VariableContext("test", OffsetDateTime.now(), true)

    @Test
    fun aProviderThrowingAnErrorNotAnExceptionDoesNotStopRendering() = runBlocking {
        val registry = VariableRegistry(
            listOf(
                Variable("a", "class missing on the device") { throw NoClassDefFoundError("org/example/Missing") },
                Variable("b", "fine") { 7 },
            )
        )
        val r = registry.render("""{"a": {{a}}, "b": {{b}}}""", true, ctx())
        assertEquals("""{"a": null, "b": 7}""", r.text)
        assertEquals(listOf("a"), r.missing)
    }
}

class WebhookDiagnosticsTest {
    @Test
    fun describeShowsClassMessageCauseAndOurFrames() {
        val t = try {
            throw IllegalStateException("outer", NoClassDefFoundError("org/example/Gone"))
        } catch (e: IllegalStateException) {
            e
        }
        val text = WebhookDiagnostics.describe(t)
        assertTrue(text.contains("java.lang.IllegalStateException: outer"))
        assertTrue(text.contains("Caused by java.lang.NoClassDefFoundError: org/example/Gone"))
        assertTrue(text.contains("describeShowsClassMessageCauseAndOurFrames"))
        assertTrue(text.lines().count { it.startsWith("  at ") } <= 3)
    }

    @Test
    fun failedProvidersAreListedWithTheReason() = runBlocking {
        val registry = VariableRegistry(listOf(Variable("a", "") { throw NoClassDefFoundError("org/example/Gone") }))
        val r = registry.render("{{a}}", true, VariableContext("test", OffsetDateTime.now(), true))
        assertEquals(listOf("a (NoClassDefFoundError: org/example/Gone)"), r.failed)
    }
}
