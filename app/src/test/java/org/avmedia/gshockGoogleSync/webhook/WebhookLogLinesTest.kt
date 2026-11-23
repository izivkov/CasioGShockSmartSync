package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.log.LogSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What reaches the app log for a webhook holds the name, the host and a short reason: no token, header value or query. */
class WebhookLogLinesTest {
    private val lines = ArrayList<String>()

    @Before fun install() {
        CallRetry.sleeper = {}
        CallRetry.writer = { _, text -> lines += text }
    }

    @After fun restore() {
        CallRetry.sleeper = { Thread.sleep(it) }
        CallRetry.writer = org.avmedia.gshockapi.log.AppLog::log
    }

    @Test fun failureLineHasNoTokenHeaderOrQuery() {
        val hook = Webhook(
            name = "Home",
            // Nothing listens on port 1: the call fails at once with a connection error.
            url = "http://user:PASSWORD@127.0.0.1:1/hook/PATHSECRET?token=QUERYSECRET",
            headers = listOf(WebhookHeader("Authorization", "Bearer HEADERSECRET")),
        )
        WebhookSender.send(hook, "{}", retries = true)
        val all = lines.joinToString("\n")
        assertEquals(all, 1, lines.size)
        assertTrue(all, all.contains("Home") && all.contains("127.0.0.1") && all.contains("failed after 4 tries"))
        for (secret in listOf("PASSWORD", "PATHSECRET", "QUERYSECRET", "HEADERSECRET", "Authorization", "token=")) {
            assertFalse("$secret in: $all", all.contains(secret))
        }
    }

    @Test fun hostOnly() {
        assertEquals("example.com", WebhookSender.hostOf("https://u:p@example.com/a/b?c=d"))
        assertEquals("invalid URL", WebhookSender.hostOf("not a url"))
    }

    @Test fun logSourceIsWebhook() {
        var source: LogSource? = null
        CallRetry.writer = { s, _ -> source = s }
        WebhookSender.send(Webhook(name = "n", url = "http://127.0.0.1:1/"), "{}", retries = false)
        assertEquals(LogSource.Webhook, source)
    }
}
