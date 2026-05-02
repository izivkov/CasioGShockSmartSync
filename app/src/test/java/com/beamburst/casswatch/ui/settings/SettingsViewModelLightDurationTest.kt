package com.beamburst.casswatch.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelLightDurationTest {

    @Test
    fun `light duration values match the updated watch options`() {
        assertEquals(
            "1.5s",
            SettingsViewModel.Light.LightDuration.ONE_POINT_FIVE_SECONDS.value
        )
        assertEquals(
            "3s",
            SettingsViewModel.Light.LightDuration.THREE_SECONDS.value
        )
    }
}
