package org.avmedia.gshockapi

import org.avmedia.gshockapi.io.WatchConditionIOFunctional
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The H5600 family reports its battery in 0x28 index 0 as a step 0..6. The main screen's battery card is shown by
 * hasBatteryLevel and filled from getBatteryLevel: both must give the value of the HA battery sensor (step * 100 / 6).
 */
class H5600BatteryTest {
    @After fun reset() = WatchInfo.reset()

    @Test fun theH5600FamilyHasABatteryLevel() {
        assertTrue(WatchInfo.infoFor("CASIO GBX-H5600").hasBatteryLevel)
        assertTrue(WatchInfo.infoFor("CASIO DW-H5600").hasBatteryLevel)
    }

    @Test fun theBatteryStepBecomesTheHaPercent() {
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        fun percent(step: Int) = WatchConditionIOFunctional.decode("28 %02X 15".format(step)).getOrThrow().batteryLevel
        assertEquals(listOf(0, 17, 33, 50, 67, 83, 100), (0..6).map { percent(it) })
    }

    @Test fun otherWatchesKeepTheirScale() {
        WatchInfo.setNameAndModel("CASIO GW-B5600")
        assertEquals(50, WatchConditionIOFunctional.decode("28 0E 15").getOrThrow().batteryLevel)
    }
}
