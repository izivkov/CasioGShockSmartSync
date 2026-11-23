package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.AppNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaPushRouterTest {
    private val delivered = ArrayList<AppNotification>()
    private var updates = 0
    private val logged = ArrayList<String>()
    private val settings = ArrayList<Map<String, Any?>>()
    private val router = HaPushRouter({ delivered += it }, { updates++ }, { logged += it }, { "20261122T200000" }, { settings += it })

    private fun push(json: String) = HaPush.parse(json) as HaPushEvent.Push

    @Test fun aNormalMessageGoesToTheWatch() {
        router.route(push("""{"type":"event","event":{"message":"Door open","title":"Alarm"}}"""))
        assertEquals(1, delivered.size)
        assertEquals(0, updates)
    }

    @Test fun updateSensorsIsNeverShownAndQueuesOnePush() {
        router.route(push("""{"type":"event","event":{"message":"command_update_sensors"}}"""))
        assertTrue(delivered.isEmpty())
        assertEquals(1, updates)
    }

    @Test fun aCommandInDataCommandIsRecognisedToo() {
        router.route(push("""{"type":"event","event":{"message":"Sensoren bijwerken","data":{"command":"command_update_sensors"}}}"""))
        assertTrue(delivered.isEmpty())
        assertEquals(1, updates)
    }

    @Test fun otherCommandsAreIgnoredAndLogged() {
        router.route(push("""{"type":"event","event":{"message":"request_location_update"}}"""))
        router.route(push("""{"type":"event","event":{"message":"command_screen_on"}}"""))
        router.route(push("""{"type":"event","event":{"message":"clear_notification","data":{"tag":"x"}}}"""))
        assertTrue(delivered.isEmpty())
        assertEquals(0, updates)
        assertEquals(3, logged.size)
        assertTrue(logged[0].contains("request_location_update"))
    }

    @Test fun aMessageThatOnlyMentionsACommandInTheMiddleIsStillAMessage() {
        assertNull(HaPush.commandOf("please run command_update_sensors later", null))
        assertEquals("command_update_sensors", HaPush.commandOf("command_update_sensors", null))
    }

    @Test fun aWatchSettingCommandGoesToTheSettingsAndNeverToTheWatch() {
        router.route(push("""{"type":"event","event":{"message":"command_watch_setting","data":{"setting":"watch_face","value":"steps"}}}"""))
        router.route(push("""{"type":"event","event":{"message":"command_watch_setting","data":{"settings":{"step_goal":9000}}}}"""))
        assertTrue(delivered.isEmpty())
        assertEquals(2, settings.size)
        assertEquals("watch_face", settings[0]["setting"])
        assertEquals("steps", settings[0]["value"])
        assertEquals(mapOf("step_goal" to 9000.0), settings[1]["settings"])
        assertTrue(logged.isEmpty())
    }
}
