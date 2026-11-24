package org.avmedia.smartsync.homeassistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Test

/** The dashboard card of the Home Assistant screen in each button state. */
class HaDashboardScreenshotTest : ScreenshotTest() {
    @Composable
    private fun Card(ui: HaDashUi, registered: Boolean = true, saved: Boolean = true) =
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
            HaDashboardCard(saved, registered, ui, {}, {})
        }

    @Test fun createLight() = snap("ha_dash_create", false) { Card(HaDashUi()) }
    @Test fun createDark() = snap("ha_dash_create", true) { Card(HaDashUi()) }
    @Test fun updateLight() = snap("ha_dash_update", false) { Card(HaDashUi(DashStatus.Ours(outdated = true))) }
    @Test fun upToDateLight() = snap("ha_dash_current", false) { Card(HaDashUi(DashStatus.Ours(outdated = false))) }
    @Test fun foreignLight() = snap("ha_dash_foreign", false) { Card(HaDashUi(DashStatus.Foreign)) }
    @Test fun workingLight() = snap("ha_dash_working", false) { Card(HaDashUi(working = true)) }
    @Test fun doneLight() = snap("ha_dash_done", false) {
        Card(HaDashUi(DashStatus.Ours(false), message = "Dashboard created with 8 cards.", url = "https://ha.example.com/gshock-c5951dde28df"))
    }
    @Test fun noAdminLight() = snap("ha_dash_no_admin", false) {
        Card(HaDashUi(message = org.avmedia.smartsync.homeassistant.HaDashboardService.NO_ADMIN, failed = true))
    }
    @Test fun noAdminDark() = snap("ha_dash_no_admin", true) {
        Card(HaDashUi(message = org.avmedia.smartsync.homeassistant.HaDashboardService.NO_ADMIN, failed = true))
    }
    @Test fun notRegisteredLight() = snap("ha_dash_not_registered", false) { Card(HaDashUi(), registered = false) }
}
