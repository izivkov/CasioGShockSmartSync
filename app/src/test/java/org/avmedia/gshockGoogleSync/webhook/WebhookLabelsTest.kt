package org.avmedia.gshockGoogleSync.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebhookLabelsTest {
    @Test fun hostHidesPathAndQuery() {
        assertEquals("ha.example.com", WebhookLabels.hostOf("https://ha.example.com/api/webhook/SECRET?x=1"))
        assertFalse(WebhookLabels.hostOf("https://h.com?token=abc").contains("abc"))
        assertEquals("(no address)", WebhookLabels.hostOf(""))
    }

    @Test fun triggersInWords() {
        assertEquals(listOf("Watch button", "Health data"), WebhookLabels.triggers(Webhook(onWatchAction = true, onSync = false, onHealthData = true)))
        assertEquals(emptyList<String>(), WebhookLabels.triggers(Webhook(onWatchAction = false)))
    }

    @Test fun resultTexts() {
        assertEquals("Not sent yet", WebhookLabels.resultText(null, null))
        assertEquals("Worked, 10:00", WebhookLabels.resultText(WebhookResult(ok = true, status = 200), "10:00"))
        assertEquals("Failed, 10:00", WebhookLabels.resultText(WebhookResult(ok = false), "10:00"))
        assertEquals("Failed, trying again, 10:00", WebhookLabels.resultText(WebhookResult(retrying = true), "10:00"))
    }
}
