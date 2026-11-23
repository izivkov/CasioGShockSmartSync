package org.avmedia.gshockGoogleSync.homeassistant

import com.google.gson.JsonParser
import org.avmedia.gshockapi.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaPushTest {
    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun wsUrlFollowsTheScheme() {
        assertEquals("wss://ha.example/api/websocket", HaPush.wsUrl("https://ha.example/"))
        assertEquals("ws://192.168.1.5:8123/api/websocket", HaPush.wsUrl("http://192.168.1.5:8123"))
        assertNull(HaPush.wsUrl("ftp://ha.example"))
    }

    @Test
    fun subscribeAsksForConfirms() {
        val o = obj(HaPush.subscribe(2, "hook1"))
        assertEquals(2, o.get("id").asInt)
        assertEquals("mobile_app/push_notification_channel", o.get("type").asString)
        assertEquals("hook1", o.get("webhook_id").asString)
        assertTrue(o.get("support_confirm").asBoolean)
    }

    @Test
    fun confirmCarriesTheConfirmId() {
        val o = obj(HaPush.confirm(3, "hook1", "abc"))
        assertEquals("mobile_app/push_notification_confirm", o.get("type").asString)
        assertEquals("hook1", o.get("webhook_id").asString)
        assertEquals("abc", o.get("confirm_id").asString)
        assertEquals(3, o.get("id").asInt)
    }

    @Test
    fun authCarriesTheToken() {
        val o = obj(HaPush.auth("tok"))
        assertEquals("auth", o.get("type").asString)
        assertEquals("tok", o.get("access_token").asString)
    }

    @Test
    fun parsesAuthSteps() {
        assertEquals(HaPushEvent.AuthRequired, HaPush.parse("""{"type":"auth_required","ha_version":"2026.1"}"""))
        assertEquals(HaPushEvent.AuthOk, HaPush.parse("""{"type":"auth_ok"}"""))
        assertEquals(HaPushEvent.AuthInvalid, HaPush.parse("""{"type":"auth_invalid","message":"x"}"""))
    }

    @Test
    fun parsesAPushWithTitleAndConfirmId() {
        val e = HaPush.parse("""{"id":2,"type":"event","event":{"message":"Door open","title":"Alarm","hass_confirm_id":"c1"}}""")
        assertEquals(HaPushEvent.Push("Alarm", "Door open", "c1", subscriptionId = 2), e)
    }

    @Test
    fun parsesAPushWithoutTitleOrConfirmId() {
        val e = HaPush.parse("""{"id":2,"type":"event","event":{"message":"Hi"}}""")
        assertEquals(HaPushEvent.Push(null, "Hi", null, subscriptionId = 2), e)
    }

    @Test
    fun ignoresResultsGarbageAndEmptyMessages() {
        assertEquals(HaPushEvent.Result(2, true, null), HaPush.parse("""{"id":2,"type":"result","success":true}"""))
        assertEquals(HaPushEvent.Ignored, HaPush.parse("not json"))
        assertEquals(HaPushEvent.Ignored, HaPush.parse("""{"id":2,"type":"event","event":{"title":"No message"}}"""))
    }

    @Test
    fun notificationUsesTitleAndMessage() {
        val n = HaPush.toNotification(HaPushEvent.Push("Alarm", "Door open", "c1"), "20261122T150400")
        assertEquals("Alarm", n.app)
        assertEquals("Door open", n.text)
        assertEquals("20261122T150400", n.timestamp)
        assertEquals(NotificationType.MESSAGE, n.type)
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        assertEquals(listOf(0L, 1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 60_000L), (0..6).map { HaPush.backoffMs(it) })
        assertEquals(60_000L, HaPush.backoffMs(40))
    }

    @Test
    fun registrationAsksForTheLocalPushChannel() {
        val body = HaPayloads.registration("gshock-1", "CASIO X", "X", "1", "14")
        assertTrue(obj(body).getAsJsonObject("app_data").get("push_websocket_channel").asBoolean)
    }

    @Test
    fun updateRegistrationCarriesTheAppDataSoOldRegistrationsGetTheFlag() {
        val body = HaPayloads.registration("gshock-1", "CASIO X", "X", "1", "14")
        val d = obj(HaPayloads.updateRegistration(body)).getAsJsonObject("data")
        assertTrue(d.getAsJsonObject("app_data").get("push_websocket_channel").asBoolean)
    }
}
