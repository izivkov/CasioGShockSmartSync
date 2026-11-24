package org.avmedia.gshockGoogleSync.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.avmedia.gshockapi.WatchInfo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * An OCW-S7000 lists the notification characteristic but shows no phone notifications: a phone notification was
 * written to it and failed ("request 20 failed: The write to the watch failed", 0x20 being the text's first byte).
 * The forwarder asks the watch model too, not only the characteristic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneNotificationModelTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun reset() {
        WatchInfo.reset()
        Dispatchers.resetMain()
    }

    @Test fun anOcwGetsNoPhoneNotificationEvenWithTheCharacteristic() {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        assertFalse(PhoneNotificationForwarder.watchShows(serviceFound = true))
    }

    @Test fun anH5600GetsThemWhenTheCharacteristicIsThere() {
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        assertTrue(PhoneNotificationForwarder.watchShows(serviceFound = true))
        assertFalse(PhoneNotificationForwarder.watchShows(serviceFound = false))
    }

    /**
     * The GBX-H5600 dropped and reconnected; a phone notification came before its services were
     * discovered and the App log said "the connected watch cannot show them". After a fresh start (nothing known about
     * the link yet) an H5600 shows them, an OCW-S7000 still does not.
     */
    @Test fun anH5600ConnectedAfterAFreshStartShowsThem() {
        WatchInfo.reset()
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        assertTrue(PhoneNotificationForwarder.watchShows(serviceFound = null))
        val kv = object : org.avmedia.gshockGoogleSync.watch.KeyValueStore {
            val m = HashMap<String, String>()
            override fun get(key: String) = m[key]
            override fun put(key: String, value: String) { m[key] = value }
            override fun delete(key: String) { m.remove(key) }
            override fun keys(): Set<String> = m.keys.toSet()
        }
        val w = "gshock-aaaaaaaaaa01"
        val settings = PhoneNotificationSettings(kv, org.avmedia.gshockGoogleSync.watch.WatchStore(kv, pickLegacyOwner = { w }))
        val lines = mutableListOf<String>()
        val f = PhoneNotificationForwarder(settings, { PhoneNotificationForwarder.watchShows(serviceFound = null) }, { w }, { lines += it })
        assertTrue("log: $lines", f.decide(PostedNotification("com.chat", flags = 0, importance = 4), "Chat", "org.avmedia.gshockGoogleSync"))
        assertTrue("log: $lines", lines.none { "cannot show" in it })
    }

    @Test fun anOcwStillGetsNoneWhileItsServicesAreNotKnown() {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        assertFalse(PhoneNotificationForwarder.watchShows(serviceFound = null))
    }
}
