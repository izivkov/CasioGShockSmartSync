package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.ui.time.BatteryBars
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryBarsTest {
    private val known = mapOf(5 to 3)

    @Test fun calibratedLevelIsExact() = assertEquals(3 to true, BatteryBars.barsFor(5, known))
    @Test fun oneStepUpIsOneMoreBar() = assertEquals(4 to false, BatteryBars.barsFor(6, known))
    @Test fun oneStepDownIsOneBarLess() = assertEquals(2 to false, BatteryBars.barsFor(4, known))
    @Test fun clampedToFourBars() = assertEquals(4 to false, BatteryBars.barsFor(9, known))
    @Test fun clampedToZero() = assertEquals(0 to false, BatteryBars.barsFor(0, known))
    @Test fun usesNearestCalibration() = assertEquals(0 to false, BatteryBars.barsFor(2, mapOf(5 to 3, 3 to 1)))
}
