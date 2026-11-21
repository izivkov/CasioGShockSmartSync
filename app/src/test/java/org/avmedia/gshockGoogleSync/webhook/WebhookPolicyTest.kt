package org.avmedia.gshockGoogleSync.webhook

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookPolicyTest {
    private val hook = Webhook(url = "https://example.com/h", onWatchAction = true, onSync = true, minIntervalMinutes = 5)
    private val t0 = 1_000_000L

    @Test
    fun syncTriggerHonoursMinimumInterval() {
        assertFalse(WebhookPolicy.shouldFire(hook, WebhookTrigger.SYNC, t0, t0 + 4 * 60_000))
        assertTrue(WebhookPolicy.shouldFire(hook, WebhookTrigger.SYNC, t0, t0 + 5 * 60_000))
        assertTrue(WebhookPolicy.shouldFire(hook, WebhookTrigger.SYNC, null, t0))
    }

    @Test
    fun disabledOrUrllessOrWrongTriggerNeverFires() {
        assertFalse(WebhookPolicy.shouldFire(hook.copy(enabled = false), WebhookTrigger.SYNC, null, t0))
        assertFalse(WebhookPolicy.shouldFire(hook.copy(url = " "), WebhookTrigger.SYNC, null, t0))
        assertFalse(WebhookPolicy.shouldFire(hook.copy(onSync = false), WebhookTrigger.SYNC, null, t0))
        assertFalse(WebhookPolicy.shouldFire(hook, WebhookTrigger.HEALTH_DATA, null, t0))
    }

    @Test
    fun retryOnlyForOfflineAndServerErrors() {
        assertTrue(WebhookPolicy.isRetryable(null))
        assertTrue(WebhookPolicy.isRetryable(503))
        assertTrue(WebhookPolicy.isRetryable(429))
        assertFalse(WebhookPolicy.isRetryable(200))
        assertFalse(WebhookPolicy.isRetryable(401))
        assertFalse(WebhookPolicy.isRetryable(404))
    }

    @Test
    fun responsePreviewIsCutAt300() {
        assertEquals(300, WebhookPolicy.preview("x".repeat(1000)).length)
        assertEquals("", WebhookPolicy.preview(null))
    }

    @Test
    fun requestHasMethodHeadersAndContentType() {
        val h = hook.copy(
            method = "PUT",
            headers = listOf(WebhookHeader("Authorization", "Bearer abc"), WebhookHeader("", "ignored")),
        )
        val req: Request = WebhookSender.buildRequest(h, """{"a":1}""")
        assertEquals("PUT", req.method)
        assertEquals("Bearer abc", req.header("Authorization"))
        assertTrue(req.body!!.contentType().toString().startsWith("application/json"))
        assertEquals(1, req.headers.size)
    }

    @Test
    fun getRequestHasNoBody() {
        assertNull(WebhookSender.buildRequest(hook.copy(method = "GET"), "ignored").body)
    }

    @Test
    fun badUrlIsAnErrorOutcomeNotACrash() {
        val outcome = WebhookSender.send(hook.copy(url = "not a url"), "{}")
        assertNull(outcome.status)
        assertFalse(outcome.ok)
    }

    @Test
    fun storedDataRoundTripsAndBadDataGivesEmpty() {
        val hooks = listOf(hook.copy(name = "A", headers = listOf(WebhookHeader("K", "V"))))
        assertEquals(hooks, WebhookJson.hooksFromJson(WebhookJson.hooksToJson(hooks)))
        assertEquals(emptyList<Webhook>(), WebhookJson.hooksFromJson("{not json"))
        assertEquals(emptyList<Webhook>(), WebhookJson.hooksFromJson(null))
        val results = mapOf("x" to WebhookResult(1L, 200, true, "ok"))
        assertEquals(results, WebhookJson.resultsFromJson(WebhookJson.resultsToJson(results)))
        assertEquals(emptyMap<String, WebhookResult>(), WebhookJson.resultsFromJson("garbage"))
    }

    @Test
    fun presetsRenderValidDefaults() {
        assertEquals("{{all}}", WebhookPresets.RAW_JSON.bodyTemplate)
        assertTrue(WebhookPresets.TRMNL.bodyTemplate.contains("merge_strategy"))
        assertTrue(WebhookPresets.TRMNL.bodyTemplate.contains("deep_merge"))
        assertEquals("text/plain", WebhookPresets.PLAIN_TEXT.contentType)
    }
}
