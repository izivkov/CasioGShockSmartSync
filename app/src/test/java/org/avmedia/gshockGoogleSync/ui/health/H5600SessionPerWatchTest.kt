package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockapi.model.StepCounterData
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The OCW-S7000 showed the GBX's health data. The last transfer belongs to the watch it came from. */
class H5600SessionPerWatchTest {
    private var watch = "gshock-aaaaaaaaaa01"

    @After fun reset() {
        H5600Session.clear()
        H5600Session.watch = { org.avmedia.gshockGoogleSync.watch.CurrentWatch.key() }
    }

    @Test fun anotherWatchNeverSeesTheTransferOfTheFirst() {
        H5600Session.watch = { watch }
        val data = StepCounterData.unavailable()
        H5600Session.data = data
        assertSame(data, H5600Session.data)
        watch = "gshock-bbbbbbbbbb02"
        assertNull(H5600Session.data)
        watch = "gshock-aaaaaaaaaa01"
        assertSame(data, H5600Session.data)
    }
}
