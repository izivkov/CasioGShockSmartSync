package org.avmedia.gshockGoogleSync.ui.health

import CachedIO
import java.io.File
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DW-H5600 Health data lives in [CachedIO], so it lasts exactly as long as the connection: the library clears the cache
 * when a connection is set up, opening the screen shows what is cached, and only the first open of a connection reads.
 */
class H5600SessionCacheTest {
    private fun transfer(steps: Int) = StepCounterData.unavailable().copy(currentDaySteps = steps)

    @After fun reset() {
        H5600Session.clear()
        H5600Session.onStored = null
        H5600Session.onDownloaded = null
        H5600Session.dayMerger = { it }
    }

    @Test fun aStoredTransferIsTheConnectionsData() = runBlocking<Unit> {
        assertNull(H5600Session.data)
        var kept: StepCounterData? = null
        var pushed = 0
        H5600Session.onStored = { kept = it }
        H5600Session.onDownloaded = { pushed++ }

        val merged = H5600Session.store(transfer(100))

        assertSame(merged, H5600Session.data)
        assertSame(merged, kept)
        assertEquals(1, pushed)
        assertSame("it is in the library's cache, not in a cache of this object", merged, CachedIO.getOrNull<StepCounterData>("H5600_LIFELOG"))
    }

    @Test fun theDayIsMergedBeforeItIsKept() = runBlocking<Unit> {
        val day = transfer(999)
        H5600Session.dayMerger = { day }
        H5600Session.store(transfer(100))
        assertSame(day, H5600Session.data)
    }

    @Test fun aFailingMergeKeepsTheTransferAsItCame() = runBlocking<Unit> {
        H5600Session.dayMerger = { error("no stored day") }
        val fresh = transfer(100)
        H5600Session.store(fresh)
        assertSame(fresh, H5600Session.data)
    }

    @Test fun aNewConnectionClearsIt() = runBlocking<Unit> {
        H5600Session.store(transfer(100))
        CachedIO.clearCache() // what the library does when a connection is set up
        assertNull(H5600Session.data)
    }

    @Test fun theLibraryClearsTheCacheBeforeItAnnouncesAConnection() {
        val manager = File("../api/src/main/java/org/avmedia/gshockapi/ble/IGShockManager.kt").readText()
        val ready = manager.substringBefore("ProgressEvents.onNext(\"ConnectionSetupComplete\", device)")
        assertTrue(ready.trimEnd().endsWith("CachedIO.clearCache()"))
    }

    @Test fun theScreenOpensFromTheCachedDataBeforeTheOpenGate() {
        val vm = File("src/main/java/org/avmedia/gshockGoogleSync/ui/health/HealthViewModel.kt").readText()
        val opened = vm.substringAfter("private fun onOpened()").substringBefore("private fun showSessionData")
        assertTrue(opened.indexOf("H5600Session.data") in 0 until opened.indexOf("HealthOpenRead.shared.tryStart()"))
    }

    @Test fun thereIsNoSecondCacheForTheWatch() {
        val session = File("src/main/java/org/avmedia/gshockGoogleSync/ui/health/H5600Session.kt").readText()
        assertFalse("a clock or a stored value of its own", "currentTimeMillis" in session || "StoredValue" in session)
        assertTrue("CachedIO.put" in session && "CachedIO.getOrNull" in session)
    }
}
