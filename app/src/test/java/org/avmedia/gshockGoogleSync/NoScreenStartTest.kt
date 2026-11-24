package org.avmedia.gshockGoogleSync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a reboot, a killed app or Bluetooth off/on the system starts the process for the watch (the companion device
 * service) and no Activity is ever created. Everything the kept watch needs must start from the Application: the
 * session wiring and Home Assistant (MainEventHandler), the HA hooks and the periodic read (HaSync and
 * BackgroundHealthSync through ActionRunner), the coordinator that admits DeviceAppeared, and the stored Keep connected.
 */
class NoScreenStartTest {
    private val main = File("src/main/java/org/avmedia/gshockGoogleSync")
    private val app = File(main, "GShockApplication.kt").readText()
    private val onCreate = app.substringAfter("override fun onCreate()").substringBefore("override fun onTerminate()")

    @Test
    fun theApplicationWiresEverythingTheKeptWatchNeeds() {
        for (field in listOf("lateinit var companionDeviceCoordinator", "lateinit var actionRunner",
            "lateinit var sessionManager", "lateinit var sessionEventBridge")) {
            assertTrue("GShockApplication injects $field", field in app)
        }
        for (step in listOf("MainEventHandler(", "setupEventSubscription()", "actionRunner.setupActionSubscriptions()",
            "sessionEventBridge.setupEventBridge()", "KeepConnected.load(", "AppLog.log(")) {
            assertTrue("onCreate runs $step", step in onCreate)
        }
        val runner = File(main, "ui/actions/ActionRunner.kt").readText()
        assertTrue("ActionRunner brings HaSync", "private val haSync: HaSync" in runner)
        assertTrue("ActionRunner brings BackgroundHealthSync", "BackgroundHealthSync" in runner)
        assertTrue("the service holds the HA push", "acquire(HaPushHost.SERVICE)" in File(main, "pairing/GShockCompanionDeviceService.kt").readText())
    }

    @Test
    fun noneOfItWaitsForTheActivity() {
        val activity = File(main, "MainActivity.kt").readText()
        for (late in listOf("MainEventHandler(", "setupEventSubscription", "setupActionSubscriptions", "setupEventBridge", "KeepConnected")) {
            assertFalse("MainActivity must not be the one to start $late", late in activity)
        }
    }
}
