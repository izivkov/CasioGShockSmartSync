package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.smartsync.homeassistant.*

import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode as M
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy as P
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationSettings
import org.avmedia.gshockGoogleSync.notifications.PostedNotification
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class NotifMemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
}

/**
 * One choice per watch, "All apps" / "Only selected apps" / "Off". "All apps" is the default and
 * behaves like upstream (it replaced the earlier "off by default" switch, which the owner decided against).
 */
class PhoneNotificationPolicyTest {
    private val own = "org.avmedia.gshockGoogleSync.homeassistant"
    private val chat = PostedNotification("com.chat", flags = 0, importance = 4)
    private val gbx = "gshock-aaaaaaaaaa01"
    private val ocw = "gshock-bbbbbbbbbb02"

    private fun settings(store: NotifMemoryStore = NotifMemoryStore(), owner: String? = gbx) =
        PhoneNotificationSettings(store, WatchStore(store, pickLegacyOwner = { owner }))

    @Test fun allAppsIsTheDefaultAndPassesEverythingLikeUpstream() {
        val s = settings()
        assertEquals(M.ALL, s.mode(gbx))
        assertTrue(s.allows(gbx, chat, own))
        // Upstream sends every notification: ongoing, silent, summaries and our own too.
        assertTrue(s.allows(gbx, chat.copy(flags = P.FLAG_ONGOING_EVENT or P.FLAG_GROUP_SUMMARY, importance = 1), own))
        assertTrue(s.allows(gbx, chat.copy(packageName = own), own))
    }

    @Test fun offSendsNothing() {
        val s = settings().apply { setMode(gbx, M.OFF) }
        assertFalse(s.allows(gbx, chat, own))
        assertEquals("phone notifications are off", s.skipReason(gbx, chat, own))
    }

    @Test fun onlySelectedPassesTheChosenAppsWithTheFilters() {
        val s = settings().apply { setMode(gbx, M.SELECTED); setAllowed(gbx, "com.chat", true) }
        assertTrue(s.allows(gbx, chat, own))
        assertEquals("app not chosen", s.skipReason(gbx, chat.copy(packageName = "com.other"), own))
        assertEquals("ongoing", P.skipReason(M.SELECTED, setOf("com.chat"), chat.copy(flags = P.FLAG_ONGOING_EVENT), own))
        assertEquals("ongoing", P.skipReason(M.SELECTED, setOf("com.chat"), chat.copy(flags = P.FLAG_FOREGROUND_SERVICE), own))
        assertEquals("group summary", P.skipReason(M.SELECTED, setOf("com.chat"), chat.copy(flags = P.FLAG_GROUP_SUMMARY), own))
        assertEquals("silent", P.skipReason(M.SELECTED, setOf("com.chat"), chat.copy(importance = 2), own))
        assertEquals("this app's own", P.skipReason(M.SELECTED, setOf("com.chat", own), chat.copy(packageName = own), own))
        assertTrue(P.allow(M.SELECTED, setOf("com.chat"), chat.copy(importance = P.IMPORTANCE_UNKNOWN), own))
        s.setAllowed(gbx, "com.chat", false)
        assertFalse(s.allows(gbx, chat, own))
    }

    @Test fun onlySelectedWithNoAppsSendsNothing() {
        assertFalse(settings().apply { setMode(gbx, M.SELECTED) }.allows(gbx, chat, own))
    }

    @Test fun theOldSwitchOnWithAppsBecomesOnlySelectedForTheOwnerWatch() {
        val store = NotifMemoryStore().apply { map["phone_notifications.enabled"] = "on"; map["phone_notifications.apps"] = "com.chat\ncom.mail" }
        val s = settings(store)
        assertEquals(M.SELECTED, s.mode(gbx))
        assertEquals(setOf("com.chat", "com.mail"), s.allowed(gbx))
        // Another watch never gets the old setting.
        assertEquals(M.ALL, s.mode(ocw))
        assertTrue(s.allowed(ocw).isEmpty())
        // Migrated once: the old keys are gone, the values stay.
        assertFalse("phone_notifications.enabled" in store.map)
        assertEquals(M.SELECTED, settings(store).mode(gbx))
    }

    @Test fun theOldSwitchTurnedOffBecomesOff() {
        val store = NotifMemoryStore().apply { map["phone_notifications.enabled"] = "off" }
        assertEquals(M.OFF, settings(store).mode(gbx))
    }

    @Test fun neverTouchedStaysAllApps() {
        assertEquals(M.ALL, settings(NotifMemoryStore()).mode(gbx))
    }

    @Test fun theModeIsPerWatch() {
        val store = NotifMemoryStore()
        settings(store).apply { setMode(gbx, M.SELECTED); setAllowed(gbx, setOf("com.chat")); setMode(ocw, M.OFF) }
        val again = settings(store)
        assertEquals(M.SELECTED, again.mode(gbx))
        assertEquals(setOf("com.chat"), again.allowed(gbx))
        assertEquals(M.OFF, again.mode(ocw))
        assertTrue(again.allowed(ocw).isEmpty())
    }

    /** The Home Assistant path hands messages straight to the watch: nothing in it asks this setting. */
    @Test fun theHomeAssistantPathDoesNotUseThisSetting() {
        val src = java.io.File("../plugins/homeassistant/src/main/java/org/avmedia/smartsync/homeassistant/HaPushChannel.kt").readText()
        assertFalse(src.contains("PhoneNotification"))
        val handler = java.io.File("src/main/java/org/avmedia/gshockGoogleSync/MainEventHandler.kt").readText()
        assertFalse(handler.contains("PhoneNotification"))
    }
}
