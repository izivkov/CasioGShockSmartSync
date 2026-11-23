package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.io.AppNotificationIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What an HA push puts in the four text fields of the watch frame (app name, title, short text, text).
 * The app name is the first line the H5600 shows; a fixed "Home Assistant" there left no room for the push (owner 2026-11-23).
 */
class HaPushWatchFieldsTest {
    private fun frame(push: HaPushEvent.Push) =
        AppNotificationIO.encodeNotificationPacket(HaPush.toNotification(push, "20261123T180000"))

    private fun decoded(push: HaPushEvent.Push) = AppNotificationIO.decodeNotificationPacket(frame(push))

    @Test
    fun theTitleIsTheFirstVisibleFieldAndNoHomeAssistantAnywhere() {
        val push = HaPushEvent.Push("Charge watch", "Battery 25%", null)
        val n = decoded(push)
        assertEquals("Charge watch", n.app)
        assertEquals("Battery 25%", n.text)
        assertFalse(String(frame(push), Charsets.UTF_8).contains("Home Assistant"))
    }

    @Test
    fun theTitleIsNotRepeatedInTheNextField() {
        val n = decoded(HaPushEvent.Push("Charge watch", "Battery 25%", null))
        assertEquals("", n.title)
    }

    @Test
    fun dataAppIsTheShortSourceNameAndTheTitleMovesToTheNextField() {
        val n = decoded(HaPushEvent.Push("Charge watch", "Battery 25%", null, data = mapOf("app" to "HA")))
        assertEquals("HA", n.app)
        assertEquals("Charge watch", n.title)
        assertEquals("Battery 25%", n.text)
    }

    @Test
    fun noTitleGivesTheShortestSourceName() {
        val push = HaPushEvent.Push(null, "Door open", null)
        assertEquals("HA", decoded(push).app)
        assertEquals("HA", decoded(push.copy(title = "  ")).app)
        assertFalse(String(frame(push), Charsets.UTF_8).contains("Home Assistant"))
    }

    @Test
    fun aVeryLongTitleStillMakesAFrame() {
        val n = decoded(HaPushEvent.Push("x".repeat(400), "Battery 25%", null))
        assertEquals(HaPush.MAX_FIRST_FIELD_CHARS, n.app.length)
    }
}
