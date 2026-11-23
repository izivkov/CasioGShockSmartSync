package org.avmedia.gshockGoogleSync.homeassistant

import java.io.File
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class PressStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

private class PressTransport : HaTransport {
    val posts = ArrayList<String>()
    override fun get(url: String, bearer: String?) = HaResponse(200, "[]")
    override fun post(url: String, body: String, bearer: String?): HaResponse {
        posts += url
        return when {
            url.endsWith("/registrations") -> HaResponse(200, """{"webhook_id":"hook1"}""")
            url.contains("/api/services/") -> HaResponse(200, "[]")
            else -> HaResponse(200, """{"success":true}""")
        }
    }
}

/**
 * The watch button (Find Phone on the always-connected watches) runs the Home Assistant action chosen on the Home
 * Assistant screen once, through [HaJobs] and [HaClient] only: the removed HTTP calls feature is not on that path.
 */
class HaButtonPressTest {
    private val now = OffsetDateTime.parse("2026-11-23T21:00:00+01:00")
    private val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")

    private fun send(trigger: HaTrigger, states: Map<String, Any>): Pair<PressTransport, HaStore>? {
        val store = HaStore(PressStore())
        store.save(HaConfig("https://ha.example", "tok"))
        store.saveAction(HaAction("automation", "automation.find_phone", "Find phone"))
        val job = HaJobs.job(trigger, now, "GBX-H5600", states, emptyMap(), "gshock-1", reg) ?: return null
        val t = PressTransport()
        assertTrue(HaClient(t, store).send(job) is HaResult.Ok)
        return t to store
    }

    @Test fun aButtonPressRunsTheChosenActionOnce() {
        val (t, store) = send(HaTrigger.WATCH_ACTION, emptyMap())!!
        assertEquals(listOf("https://ha.example/api/services/automation/trigger"), t.posts.filter { "/api/services/" in it })
        assertTrue(store.lastActionResult().orEmpty().startsWith("Ran Find phone"))
    }

    @Test fun theButtonEventNamesTheWatchAndTheTrigger() {
        val e = HaJobs.event(HaTrigger.WATCH_ACTION, now, "GBX-H5600")!!
        assertEquals("watch_action", e["trigger"])
        assertEquals("GBX-H5600", e["watch"])
    }

    @Test fun aSyncSendsTheSensorsAndRunsNoAction() {
        val (t, _) = send(HaTrigger.SYNC, mapOf("battery" to 80))!!
        assertTrue(t.posts.none { "/api/services/" in it })
        assertNull(send(HaTrigger.SYNC, emptyMap()))
    }

    @Test fun theButtonActionGoesStraightToHomeAssistant() {
        val src = File("src/main/java/org/avmedia/gshockGoogleSync/ActionContainer.kt").readText()
        val action = src.substringAfter("inner class HaButtonAction").substringBefore("inner class Separator")
        assertTrue(action, "haSync.fire(HaTrigger.WATCH_ACTION)" in action)
    }

    /** No screen, string, icon, log source or class of the removed feature is left (HA's own mobile_app webhook stays). */
    @Test fun nothingOfTheRemovedFeatureIsLeft() {
        val gone = listOf("WebhookSender", "WebhookDispatcher", "WebhookWorker", "WebhooksScreen", "WebhookStorage",
            "TemplateRenderer", "R.string.webhook", "ic_webhook", "LogSource.Webhook", "Service.WEBHOOKS", "onOpenWebhooks")
        val roots = listOf(File("src/main/java"), File("../api/src/main/java"))
        val hits = roots.flatMap { r -> r.walk().filter { it.extension == "kt" }.toList() }
            .flatMap { f -> f.readText().let { text -> gone.filter { it in text }.map { "${f.name}: $it" } } }
        assertEquals(emptyList<String>(), hits)
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue("strings.xml still has a webhooks entry", !Regex("""name="[a-z_]*webhook""").containsMatchIn(strings))
        assertTrue(!File("src/main/res/drawable/ic_webhook.xml").exists())
    }
}
