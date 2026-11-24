package org.avmedia.gshockGoogleSync.notifications

import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode.ALL
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode.OFF
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode.SELECTED
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy.FLAG_FOREGROUND_SERVICE
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy.FLAG_GROUP_SUMMARY
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy.FLAG_ONGOING_EVENT
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy.IMPORTANCE_UNKNOWN
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationPolicy.skipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNotificationPolicyTest {
    private val own = "org.avmedia.gshockGoogleSync"
    private val chat = PostedNotification("com.chat", flags = 0, importance = 4)
    private val chosen = setOf("com.chat")

    private fun settings(store: MutableMap<String, String> = mutableMapOf()) =
        PhoneNotificationSettings(read = { store[it] }, write = { k, v -> store[k] = v })

    @Test
    fun nothingStoredMeansAllApps() {
        val s = settings()
        assertEquals(ALL, s.mode)
        assertEquals(emptySet<String>(), s.selectedApps)
        assertNull(s.skipReason(chat, own))
    }

    @Test
    fun allAppsSendsEverything() {
        val noisy = chat.copy(flags = FLAG_ONGOING_EVENT or FLAG_GROUP_SUMMARY, importance = 1)
        assertNull(skipReason(ALL, emptySet(), noisy, own))
        assertNull(skipReason(ALL, emptySet(), chat.copy(packageName = own), own))
    }

    @Test
    fun offSendsNothing() {
        assertEquals("phone notifications are off", skipReason(OFF, chosen, chat, own))
    }

    @Test
    fun selectedSendsOnlyTheChosenApps() {
        assertNull(skipReason(SELECTED, chosen, chat, own))
        assertEquals("app not selected", skipReason(SELECTED, chosen, chat.copy(packageName = "com.other"), own))
        assertEquals("app not selected", skipReason(SELECTED, emptySet(), chat, own))
    }

    @Test
    fun selectedSkipsOngoingSilentSummaryAndOwn() {
        assertEquals("ongoing", skipReason(SELECTED, chosen, chat.copy(flags = FLAG_ONGOING_EVENT), own))
        assertEquals("ongoing", skipReason(SELECTED, chosen, chat.copy(flags = FLAG_FOREGROUND_SERVICE), own))
        assertEquals("group summary", skipReason(SELECTED, chosen, chat.copy(flags = FLAG_GROUP_SUMMARY), own))
        assertEquals("silent", skipReason(SELECTED, chosen, chat.copy(importance = 2), own))
        assertEquals("own notification", skipReason(SELECTED, chosen + own, chat.copy(packageName = own), own))
        assertNull(skipReason(SELECTED, chosen, chat.copy(importance = IMPORTANCE_UNKNOWN), own))
    }

    @Test
    fun settingsAreStoredAndReadBack() {
        val store = mutableMapOf<String, String>()
        settings(store).apply {
            mode = SELECTED
            selectedApps = setOf("com.mail", "com.chat")
        }
        val again = settings(store)
        assertEquals(SELECTED, again.mode)
        assertEquals(setOf("com.chat", "com.mail"), again.selectedApps)
        assertEquals("app not selected", again.skipReason(chat.copy(packageName = "com.other"), own))
    }

    @Test
    fun unknownStoredModeFallsBackToAllApps() {
        assertEquals(ALL, settings(mutableMapOf(PhoneNotificationSettings.KEY_MODE to "bogus")).mode)
    }
}
