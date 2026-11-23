package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockapi.io.AppNotificationIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.avmedia.gshockapi.NotificationType
import org.junit.Test

/**
 * What an HA push puts in the four text fields of the watch frame (app name, title, short text, text).
 * The app name is the first line the H5600 shows; a fixed "Home Assistant" there left no room for the push.
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
    fun theTitleIsInBothThePopUpAndTheListField() {
        // With the title only in the app name field the list and detail view showed an empty title.
        val n = decoded(HaPushEvent.Push("Charge watch", "Battery 25%", null))
        assertEquals("Charge watch", n.app)
        assertEquals("Charge watch", n.title)
        assertEquals("Battery 25%", n.text)
    }

    @Test
    fun dataAppIsTheShortSourceNameAndTheTitleMovesToTheNextField() {
        val n = decoded(HaPushEvent.Push("Charge watch", "Battery 25%", null, data = mapOf("app" to "HA")))
        assertEquals("HA", n.app)
        assertEquals("Charge watch", n.title)
        assertEquals("Battery 25%", n.text)
    }

    /** The type byte of the frame (the watch's icon) follows data.type; unknown values are logged and give message. */
    @Test
    fun dataTypeSetsTheFramesTypeByte() {
        val push = HaPushEvent.Push("Dentist", "10:30", null)
        assertEquals(NotificationType.MESSAGE, decoded(push).type)
        mapOf("message" to NotificationType.MESSAGE, "calendar" to NotificationType.CALENDAR, "email" to NotificationType.EMAIL,
            "call" to NotificationType.PHONE_CALL, "generic" to NotificationType.GENERIC, " Calendar " to NotificationType.CALENDAR,
        ).forEach { (word, type) -> assertEquals(word, type, decoded(push.copy(data = mapOf("type" to word))).type) }
        val logged = ArrayList<String>()
        val n = HaPush.toNotification(push.copy(data = mapOf("type" to "fax")), "20261123T180000") { logged += it }
        assertEquals(NotificationType.MESSAGE, AppNotificationIO.decodeNotificationPacket(AppNotificationIO.encodeNotificationPacket(n)).type)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().contains("fax"))
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
        assertEquals(HaPush.MAX_FIRST_FIELD_BYTES, n.app.toByteArray().size)
        assertEquals("é".repeat(16), decoded(HaPushEvent.Push("é".repeat(40), "m", null)).app)
    }
}
