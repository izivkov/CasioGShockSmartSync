package org.avmedia.gshockapi

import org.avmedia.gshockapi.io.WatchConditionIOFunctional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The H5600 family reports its battery in 0x28 index 0 as a step 0..6. The main screen's battery card is shown by
 * hasBatteryLevel and filled from getBatteryLevel: both must give the value of the HA battery sensor (step * 100 / 6).
 */
class H5600BatteryTest {
    private fun percent(name: String, answer: String): Int {
        val info = WatchInfo.infoFor(name)
        return WatchConditionIOFunctional.decode(answer, info.batteryLevelLowerLimit, info.batteryLevelUpperLimit).getOrThrow().batteryLevel
    }

    @Test fun theH5600FamilyHasABatteryLevel() {
        assertTrue(WatchInfo.infoFor("CASIO GBX-H5600").hasBatteryLevel)
        assertTrue(WatchInfo.infoFor("CASIO DW-H5600").hasBatteryLevel)
    }

    @Test fun theBatteryStepBecomesTheHaPercent() {
        assertEquals(listOf(0, 17, 33, 50, 67, 83, 100), (0..6).map { percent("CASIO GBX-H5600", "28 %02X 15".format(it)) })
    }

    @Test fun otherWatchesKeepTheirScale() {
        assertEquals(50, percent("CASIO GW-B5600", "28 0E 15"))
        assertEquals(100, percent("CASIO GW-B5600", "28 13 15"))
    }
}
