package org.avmedia.gshockGoogleSync.homeassistant

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The push socket as Home Assistant runs it (homeassistant/components/mobile_app, push_notification.py and
 * websocket_api.py): a push carries `hass_confirm_id`; a push not confirmed within 10 s tears the channel down
 * without a word on the socket, and a confirm then gets `not_found`. With a wrong confirm key
 * HA drops the channel 10 s after the first push and the sensor still says connected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HaPushSessionTest {
    private class Rig(val up: () -> Unit = {}) {
        val sent = ArrayList<JsonObject>()
        val routed = ArrayList<Pair<String, Int>>()
        val logs = ArrayList<String>()
        var ups = 0
        var ended: String? = null
        val session = HaPushSession(
            token = "tok",
            webhookId = "hook1",
            send = { sent += JsonParser.parseString(it).asJsonObject; true },
            up = { ups++; up() },
            end = { ended = it },
            route = { routed += it.message to sent.size },
            log = { logs += it },
        )

        fun of(type: String) = sent.filter { it.get("type")?.asString == type }
        fun subId() = of("mobile_app/push_notification_channel").last().get("id").asInt
        fun confirmId() = of("mobile_app/push_notification_confirm").last().get("id").asInt

        fun login() {
            session.onText("""{"type":"auth_required","ha_version":"2026.11.1"}""")
            session.onText("""{"type":"auth_ok","ha_version":"2026.11.1"}""")
        }
        fun subscribed() { login(); session.onText("""{"id":${subId()},"type":"result","success":true,"result":null}""") }
        // Verbatim shape of event_message(msg["id"], data) with data from PushChannel.async_send_notification.
        fun push(message: String, confirm: String) =
            session.onText("""{"id":${subId()},"type":"event","event":{"message":"$message","hass_confirm_id":"$confirm"}}""")
        fun confirmRefused() = session.onText(
            """{"id":${confirmId()},"type":"result","success":false,"error":{"code":"not_found","message":"Push notification channel not found"}}"""
        )
    }

    @Test fun answersTheConfirmHomeAssistantAsksFor() {
        val r = Rig()
        r.subscribed()
        r.push("Door open", "9f2c")
        val c = r.of("mobile_app/push_notification_confirm").single()
        assertEquals("9f2c", c.get("confirm_id").asString)
        assertEquals("hook1", c.get("webhook_id").asString)
        assertEquals(listOf("Door open"), r.routed.map { it.first })
    }

    @Test fun confirmsBeforeTheMessageIsHandled() {
        // HA waits 10 s; a slow watch write must not make the confirm late.
        val r = Rig()
        r.subscribed()
        r.push("Door open", "9f2c")
        val sentBeforeRoute = r.routed.single().second
        assertTrue("confirm not sent before the message was handled", r.sent.take(sentBeforeRoute).any { it.get("type").asString == "mobile_app/push_notification_confirm" })
    }

    @Test fun upOnlyWhenHomeAssistantAcceptedTheSubscription() {
        val r = Rig()
        r.login()
        r.session.onText("""{"id":${r.subId() + 40},"type":"result","success":true,"result":null}""")
        assertEquals("a success for another command is not the subscription", 0, r.ups)
        r.session.onText("""{"id":${r.subId()},"type":"result","success":true,"result":null}""")
        assertEquals(1, r.ups)
        assertTrue(r.logs.contains("Home Assistant: push subscribed"))
    }

    @Test fun aRefusedSubscriptionIsNotUpAndEndsTheSocket() {
        val r = Rig()
        r.login()
        r.session.onText("""{"id":${r.subId()},"type":"result","success":false,"error":{"code":"not_found","message":"Webhook ID not found"}}""")
        assertEquals(0, r.ups)
        assertEquals("subscribe refused (not_found)", r.ended)
    }

    @Test fun aSubscriptionLostWhileTheSocketIsOpenIsDetected() {
        val r = Rig()
        r.subscribed()
        r.push("Door open", "9f2c")
        r.confirmRefused()
        assertEquals("subscription lost (confirm not_found)", r.ended)
    }

    @Test fun theSensorTurnsOffWhenTheSubscriptionIsGoneAndOnWhenSubscribedAgain() = runTest {
        val states = ArrayList<Boolean>()
        val logs = ArrayList<String>()
        val subscribes = ArrayList<Long>()
        var calls = 0
        val loop = HaPushLoop(
            connect = { up ->
                calls++
                val r = Rig(up)
                r.subscribed()
                subscribes += currentTime
                if (calls == 1) { r.push("Door open", "9f2c"); r.confirmRefused() }
                r.ended ?: awaitCancellation() // the socket is still open
            },
            now = { currentTime },
            log = { logs += it },
            onState = { states += it },
        )
        backgroundScope.launch { loop.run() }
        advanceTimeBy(5_000); runCurrent()
        assertEquals("sensor on, off when HA dropped the channel, on after the new subscription", listOf(true, false, true), states)
        assertEquals("subscribed again at once", listOf(0L, 0L), subscribes)
        assertTrue(logs.toString(), logs.contains("Home Assistant: push connection lost (subscription lost (confirm not_found))"))
    }
}
