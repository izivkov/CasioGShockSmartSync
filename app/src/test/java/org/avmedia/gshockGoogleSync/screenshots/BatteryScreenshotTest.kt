package org.avmedia.gshockGoogleSync.screenshots

import org.avmedia.gshockGoogleSync.ui.time.BatteryIcon
import org.junit.Test

/** The main screen's battery (H5600: step 3 of 6 = 50 %, full = 100 %). */
class BatteryScreenshotTest : ScreenshotTest() {
    @Test fun halfLight() = snap("battery_50", false) { BatteryIcon(50) }
    @Test fun halfDark() = snap("battery_50", true) { BatteryIcon(50) }
    @Test fun fullLight() = snap("battery_100", false) { BatteryIcon(100) }
}
