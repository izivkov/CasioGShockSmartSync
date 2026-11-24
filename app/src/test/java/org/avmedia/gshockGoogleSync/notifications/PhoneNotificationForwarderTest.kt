package org.avmedia.gshockGoogleSync.notifications

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockGoogleSync.homeassistant.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-24: after re-pairing, phone notifications stopped and the App log said nothing at all. Each forwarded
 * notification now has one line; a watch without notifications gets none; lost notification access is shown.
 */
class PhoneNotificationForwarderTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    private val own = "org.avmedia.gshockGoogleSync"
    private val gbx = "gshock-aaaaaaaaaa01"
    private val ocw = "gshock-bbbbbbbbbb02"
    private val chat = PostedNotification("com.chat", flags = 0, importance = 4)
    private val kv = Mem()
    private val settings = PhoneNotificationSettings(kv, WatchStore(kv, pickLegacyOwner = { gbx }))
    private val logs = ArrayList<String>()
    private var watch = gbx
    private var supported = true
    private var now = 0L
    private val forwarder = PhoneNotificationForwarder(settings, { supported }, { watch }, { logs += it }, clock = { now })

    @Test fun eachNotificationHasOneLineWithTheAppAndNoText() {
        assertTrue(forwarder.decide(chat, "Chat", own))
        assertEquals(listOf("Phone notification from Chat: sent"), logs)
        settings.setMode(gbx, PhoneNotificationMode.SELECTED)
        assertFalse(forwarder.decide(chat, "Chat", own))
        assertEquals("Phone notification from Chat: skipped (app not chosen)", logs.last())
    }

    @Test fun theSameSkipIsNotRepeatedForAMinute() {
        settings.setMode(gbx, PhoneNotificationMode.SELECTED)
        settings.setAllowed(gbx, "com.music", true)
        val music = PostedNotification("com.music", PhoneNotificationPolicy.FLAG_ONGOING_EVENT, 4)
        repeat(5) { forwarder.decide(music, "Music", own) }
        assertEquals(1, logs.size)
        now = 61_000L
        forwarder.decide(music, "Music", own)
        assertEquals(2, logs.size)
    }

    @Test fun aWatchWithoutNotificationsGetsNoWriteAndOneLinePerSession() {
        watch = ocw
        supported = false
        repeat(3) { assertFalse(forwarder.decide(chat, "Chat", own)) }
        assertEquals(1, logs.size)
        assertTrue(logs[0], logs[0].contains("cannot show"))
        forwarder.newSession()
        forwarder.decide(chat, "Chat", own)
        assertEquals(2, logs.size)
    }

    @Test fun eachWatchUsesItsOwnChoice() {
        settings.setMode(gbx, PhoneNotificationMode.OFF)
        assertFalse(forwarder.decide(chat, "Chat", own))
        watch = "gshock-cccccccccc03"
        assertTrue(forwarder.decide(chat, "Chat", own))
    }

    @Test fun accessOffIsShownAndLoggedOnce() {
        var granted = false
        val access = NotificationAccessCheck({ granted }, { logs += it })
        assertNotNull(access.warning(PhoneNotificationMode.ALL))
        assertNotNull(access.warning(PhoneNotificationMode.SELECTED))
        assertEquals(1, logs.size)
        assertTrue(logs[0], logs[0].contains("notification access"))
        assertNull("off needs no access", access.warning(PhoneNotificationMode.OFF))
        granted = true
        assertNull(access.warning(PhoneNotificationMode.ALL))
    }

    /** Owner 2026-11-24: "Choose apps" froze the screen for seconds; the list is read off the main thread, once. */
    @Test fun theAppListIsReadOffTheCallersThreadAndOncePerSession() = runBlocking {
        val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        var loads = 0
        var loadedOn: Thread? = null
        val cache = SessionListCache(io) { loads++; loadedOn = Thread.currentThread(); listOf(AppEntry("a", "A")) }
        assertEquals(1, cache.get().size)
        assertEquals(1, cache.get().size)
        assertEquals(1, loads)
        assertNotEquals(Thread.currentThread(), loadedOn)
        io.close()
    }
}
