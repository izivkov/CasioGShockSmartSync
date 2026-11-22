package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy as P
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationSettings
import org.avmedia.gshockGoogleSync.notifications.PostedNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class NotifMemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

class PhoneNotificationPolicyTest {
    private val own = "org.avmedia.gshockGoogleSync.webhook"
    private val chat = PostedNotification("com.chat", flags = 0, importance = 4)

    private fun settings() = PhoneNotificationSettings(NotifMemoryStore())

    @Test fun offByDefaultNothingIsForwarded() {
        val s = settings()
        s.allowed = setOf("com.chat")
        assertFalse(s.enabled)
        assertFalse(s.allows(chat, own))
        assertFalse(PhoneNotificationSettings(NotifMemoryStore()).allows(chat, own))
    }

    @Test fun onlyAllowedPackagesPass() {
        val s = settings().apply { enabled = true; setAllowed("com.chat", true) }
        assertTrue(s.allows(chat, own))
        assertFalse(s.allows(chat.copy(packageName = "com.other"), own))
        s.setAllowed("com.chat", false)
        assertFalse(s.allows(chat, own))
    }

    @Test fun enabledWithNoAppsForwardsNothing() {
        assertFalse(settings().apply { enabled = true }.allows(chat, own))
    }

    @Test fun ongoingNotificationsAreDropped() {
        val ok = setOf("com.chat")
        assertFalse(P.allow(true, ok, chat.copy(flags = P.FLAG_ONGOING_EVENT), own))
        assertFalse(P.allow(true, ok, chat.copy(flags = P.FLAG_FOREGROUND_SERVICE), own))
    }

    @Test fun silentAndLowImportanceAreDropped() {
        val ok = setOf("com.chat")
        assertFalse(P.allow(true, ok, chat.copy(importance = 2), own))
        assertFalse(P.allow(true, ok, chat.copy(importance = 1), own))
        assertTrue(P.allow(true, ok, chat.copy(importance = 3), own))
        assertTrue(P.allow(true, ok, chat.copy(importance = P.IMPORTANCE_UNKNOWN), own))
    }

    @Test fun groupSummariesAndTheAppsOwnAreDropped() {
        val ok = setOf("com.chat", own)
        assertFalse(P.allow(true, ok, chat.copy(flags = P.FLAG_GROUP_SUMMARY), own))
        assertFalse(P.allow(true, ok, chat.copy(packageName = own), own))
    }

    @Test fun theSettingIsKeptInTheStore() {
        val store = NotifMemoryStore()
        PhoneNotificationSettings(store).apply { enabled = true; allowed = setOf("b", "a") }
        val again = PhoneNotificationSettings(store)
        assertTrue(again.enabled)
        assertEquals(setOf("a", "b"), again.allowed)
    }

    /** The Home Assistant path hands messages straight to the watch: nothing in it asks this setting. */
    @Test fun theHomeAssistantPathDoesNotUseThisSetting() {
        val src = java.io.File("src/main/java/org/avmedia/gshockGoogleSync/webhook/HaPushChannel.kt").readText()
        assertFalse(src.contains("PhoneNotification"))
        val handler = java.io.File("src/main/java/org/avmedia/gshockGoogleSync/MainEventHandler.kt").readText()
        assertFalse(handler.contains("PhoneNotification"))
    }
}
