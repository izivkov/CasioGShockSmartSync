package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeResultTest {
    @Test
    fun jsonHasEventWatchTimeAndOneResultPerAnsweredRequest() {
        val result = ProbeResult(
            watchName = "GBX-H5600",
            phoneTime = "2026-11-22T10:00:00+01:00",
            entries = listOf(
                ProbeEntry(0x28, listOf("0x28 64 00 1E")),
                ProbeEntry(0x28, listOf("0x28 63 00 1F")),
                ProbeEntry(0x23, emptyList()),
                ProbeEntry(0x22), // not sent yet: left out
            ),
        )
        val json = JsonParser.parseString(result.toJson()).asJsonObject
        assertEquals("probe", json.get("event").asString)
        assertEquals("GBX-H5600", json.get("watch_name").asString)
        assertEquals("2026-11-22T10:00:00+01:00", json.get("phone_time").asString)
        assertFalse(json.get("complete").asBoolean)
        assertEquals(4, json.get("requested").asInt)
        assertEquals(2, json.get("answered").asInt)
        val results = json.getAsJsonArray("results")
        assertEquals(3, results.size())
        assertEquals("0x28", results[0].asJsonObject.get("code").asString)
        assertEquals("0x28 64 00 1E", results[0].asJsonObject.getAsJsonArray("answers")[0].asString)
        assertEquals("0x23", results[2].asJsonObject.get("code").asString)
        assertEquals(0, results[2].asJsonObject.getAsJsonArray("answers").size())
    }

    @Test
    fun completeWhenEveryRequestIsDone() {
        val r = ProbeResult("w", "t", listOf(ProbeEntry(0x10, listOf("0x10 01"))))
        assertTrue(JsonParser.parseString(r.toJson()).asJsonObject.get("complete").asBoolean)
    }

    @Test
    fun planStartsWithBatteryThreeTimesAndAllAddsTheRest() {
        assertEquals(listOf(0x28, 0x28, 0x28, 0x23, 0x22), ProbePlan.DEFAULT.take(5))
        assertTrue(ProbePlan.DEFAULT.containsAll(listOf(0x3B, 0x47, 0x45, 0x43, 0x2D)))
        assertEquals(ProbePlan.DEFAULT, ProbePlan.codes(false))
        assertTrue(ProbePlan.ALL.size > ProbePlan.DEFAULT.size)
        assertEquals(ProbePlan.DEFAULT, ProbePlan.ALL.take(ProbePlan.DEFAULT.size))
        // every code 0x01..0x4F appears, and codes beyond the default list appear once
        assertTrue((0x01..0x4F).all { it in ProbePlan.ALL })
        val extra = ProbePlan.ALL.drop(ProbePlan.DEFAULT.size)
        assertEquals(extra.distinct(), extra)
        assertTrue(ProbePlan.ALL.all { it in 0x01..0x4F })
    }

    @Test
    fun probeTriggerFiresAnEnabledWebhookRegardlessOfItsTriggersOrInterval() {
        val hook = Webhook(url = "https://example.com", onWatchAction = false, onSync = false, minIntervalMinutes = 60)
        assertTrue(WebhookPolicy.shouldFire(hook, WebhookTrigger.PROBE, System.currentTimeMillis(), System.currentTimeMillis()))
        assertFalse(WebhookPolicy.shouldFire(hook.copy(enabled = false), WebhookTrigger.PROBE, null, 0))
        assertEquals("probe", WebhookTrigger.PROBE.eventName)
    }
}
