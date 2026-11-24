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
}
