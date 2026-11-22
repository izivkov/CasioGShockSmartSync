package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.webhook.HaConfig
import org.avmedia.gshockGoogleSync.webhook.HaScreenContent
import org.junit.Test

/** Home Assistant screen: not set up, set up and working, error. */
class HaScreenshotTest : ScreenshotTest() {

    private val configured = HaConfig(
        baseUrl = "https://ha.example.com",
        token = "secret-token",
        deviceName = "My G-Shock",
        model = "GBX-H5600",
    )

    @Composable
    private fun Screen(form: HaConfig, status: String) =
        HaScreenContent(form = form, status = status, onBack = {}, onForget = {}, onSave = { _, _, _, _ -> })

    @Test fun notSetUpLight() = snap("ha_not_set_up", false) { Screen(HaConfig(), "") }
    @Test fun notSetUpDark() = snap("ha_not_set_up", true) { Screen(HaConfig(), "") }
    @Test fun workingLight() = snap("ha_working", false) { Screen(configured, "Registered 1 watch, 12 sensors updated") }
    @Test fun workingDark() = snap("ha_working", true) { Screen(configured, "Registered 1 watch, 12 sensors updated") }
    @Test fun errorLight() = snap("ha_error", false) { Screen(configured, "Failed: HTTP 401 Unauthorized (check the token)") }
    @Test fun errorDark() = snap("ha_error", true) { Screen(configured, "Failed: HTTP 401 Unauthorized (check the token)") }
}
