package org.avmedia.smartsync.homeassistant

import java.util.Collections
import org.avmedia.smartsync.plugin.api.Redact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * No token, webhook id, URL with a path or notification text in the plugin's log lines (they reach logcat and the
 * App log), whatever fails: a refused token, a server error, a network error that names the URL, a dropped message.
 */
class HaRedactionTest {
    private val token = "eyJ-secret-token-0123456789"
    private val hook = "hook-secret-9876"
    private val text = "Front door left open, code 4711"
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) { lines += message + (t?.message ?: "") }
    }

    @Before fun plant() {
        Timber.plant(tree)
        CallRetry.sleeper = {}
        CallRetry.resetFailureWindow()
    }

    @After fun uproot() {
        Timber.uproot(tree)
        CallRetry.sleeper = { Thread.sleep(it) }
        CallRetry.writer = AppLog::log
    }

    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    private fun store(): HaStore = HaStore(Mem()).apply {
        save(HaConfig("https://ha.example:8123", token))
        confirmName("gshock-aabbccddeeff", "GBX")
        saveRegistration("gshock-aabbccddeeff", HaRegistration(hook, HaPayloads.registration("gshock-aabbccddeeff", "GBX", "GBX-H5600", "1", "14")))
    }

    private fun assertClean() {
        val all = lines.joinToString("\n")
        for (secret in listOf(token, hook, text, "/api/webhook/")) assertTrue("'$secret' in:\n$all", secret !in all)
    }

    @Test fun failedSendsLogNoSecret() {
        CallRetry.writer = AppLog::log
        val job = HaJob(mapOf("battery" to 80), event = mapOf("trigger" to "watch_action"), deviceId = "gshock-aabbccddeeff", registration = "{}")
        for (answer in listOf(
            HaTransport { _, _, _ -> HaResponse(401, "token $token refused") },
            HaTransport { _, _, _ -> HaResponse(500, "error at /api/webhook/$hook") },
            HaTransport { url, _, _ -> throw java.io.IOException("failed to connect to $url") },
        )) {
            runCatching { HaClient(RetryingHaTransport(answer), store()).send(job) }
        }
        assertTrue("something was logged", lines.isNotEmpty())
        assertClean()
    }

    @Test fun aDroppedMessageNamesNoText() {
        val gate = HaPushGate(store(), { "" }) { AppLog.log(LogSource.HomeAssistant, it) }
        gate.accept(hook, HaPushEvent.Push("Door", text, null))
        assertEquals(1, lines.size)
        assertClean()
    }

    @Test fun theRedactHelpersMaskTokenAndUrl() {
        AppLog.log(LogSource.HomeAssistant, "token ${Redact.secret(token)} at ${Redact.url("https://ha.example:8123/api/webhook/$hook?x=1")}")
        assertEquals("token ey…(${token.length}) at https://ha.example:8123/…", lines.single())
        assertClean()
    }
}
