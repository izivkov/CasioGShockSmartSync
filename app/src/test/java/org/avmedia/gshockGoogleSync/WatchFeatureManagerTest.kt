package org.avmedia.gshockGoogleSync

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.ui.common.WatchFeatureManager
import org.avmedia.gshockapi.WatchInfo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchFeatureManagerTest {

    private lateinit var manager: WatchFeatureManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        manager = WatchFeatureManager(mockk<SessionManager>(relaxed = true))
    }

    @After
    fun tearDown() {
        WatchInfo.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun ocwS400ShowsOnlyTimeSyncFeatures() {
        WatchInfo.setNameAndModel("CASIO OCW-S400")

        listOf(
            "alarms",
            "time.timer",
            "actions.configurable",
            "actions.reminders",
            "actions.find_phone",
            "time.world_cities",
            "time.home_time",
            "time.step_counter",
            "time_adjustment.minutes",
            "settings.watch_settings",
            "locale_card",
            "light_card",
            "operation_tone_card",
            "power_saving_card",
            "font_card",
        ).forEach { assertFalse(it, manager.isFeatureSupported(it)) }

        listOf(
            "time.battery",
            "time.temperature",
            "time_adjustment_card",
            "time_adjustment.supported",
        ).forEach { assertTrue(it, manager.isFeatureSupported(it)) }
    }

    @Test
    fun otherWatchesKeepTheirFeatures() {
        WatchInfo.setNameAndModel("CASIO GW-B5600")

        listOf(
            "alarms",
            "time.timer",
            "actions.configurable",
            "settings.watch_settings",
            "time_adjustment.minutes",
            "light_card",
            "operation_tone_card",
        ).forEach { assertTrue(it, manager.isFeatureSupported(it)) }
    }
}
