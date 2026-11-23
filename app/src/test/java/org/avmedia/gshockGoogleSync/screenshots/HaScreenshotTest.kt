package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.homeassistant.HaAction
import org.avmedia.gshockGoogleSync.homeassistant.HaActionsUi
import org.avmedia.gshockGoogleSync.homeassistant.HaCheckUi
import org.avmedia.gshockGoogleSync.homeassistant.HaConfig
import org.avmedia.gshockGoogleSync.homeassistant.HaFindUi
import org.avmedia.gshockGoogleSync.homeassistant.HaFound
import org.avmedia.gshockGoogleSync.homeassistant.HaGroup
import org.avmedia.gshockGoogleSync.homeassistant.HaGroupUi
import org.avmedia.gshockGoogleSync.homeassistant.HaScreenContent
import org.avmedia.gshockGoogleSync.homeassistant.HaSentUi
import org.avmedia.gshockGoogleSync.homeassistant.HaUi
import org.avmedia.gshockGoogleSync.homeassistant.HaWatchUi
import org.junit.Test

/** Home Assistant screen: not set up, working, errors, loading and the picker. */
class HaScreenshotTest : ScreenshotTest() {

    private val configured = HaConfig("https://ha.example.com", "secret-token", "My G-Shock", "GBX-H5600")
    private val goodnight = HaAction("automation", "automation.goodnight", "Goodnight")
    private val items = listOf(goodnight, HaAction("script", "script.bedtime", "Bedtime"), HaAction("scene", "scene.movie", "Movie night"))

    private val working = HaUi(
        form = configured,
        check = HaCheckUi.Ok("Home Assistant answered. The address and the token work."),
        watch = HaWatchUi(registered = true, firmware = "1.2", lastUpdate = "22 Nov, 15:04", deviceUrl = "https://ha.example.com/config/devices/dashboard"),
        sent = HaSentUi(
            listOf(
                HaGroupUi(HaGroup.WATCH, true, listOf("Battery" to "82 %", "Temperature" to "21 °C", "Watch connected" to "Yes")),
                HaGroupUi(HaGroup.HEALTH, true, listOf("Steps today" to "6420 steps", "Energy today" to "310 kcal", "Resting heart rate" to "58 bpm",
                    "Max heart rate" to "121 bpm", "Min heart rate" to "54 bpm", "Active minutes" to "37 min", "Walking today" to "41 min")),
                HaGroupUi(HaGroup.SETTINGS, false, emptyList()),
            )
        ),
        actions = HaActionsUi(items = items, chosen = goodnight, lastRun = "Ran Goodnight at 22:14"),
    )

    private val emptyWatch = HaUi(
        form = configured,
        watch = HaWatchUi(deviceUrl = "https://ha.example.com/config/devices/dashboard"),
        sent = HaSentUi(HaGroup.entries.map { HaGroupUi(it, true, emptyList()) }),
        actions = HaActionsUi(),
    )

    private val failing = working.copy(
        check = HaCheckUi.Error("Home Assistant did not accept the token. Create a new token and paste it again.", "HTTP 401"),
        watch = working.watch.copy(failed = true, lastText = "HTTP 503 at 2026-11-22T15:04:00+01:00"),
        sent = working.sent.copy(failed = true),
        actions = HaActionsUi(error = "Home Assistant refused the token, so the list was not run"),
    )

    private val loading = emptyWatch.copy(
        check = HaCheckUi.Checking,
        find = HaFindUi.Searching(listOf(HaFound("Home", "http://192.168.1.5:8123"), HaFound("Cabin", "http://192.168.1.9:8123"))),
        watch = emptyWatch.watch.copy(lookingUpDevice = true),
        sent = emptyWatch.sent.copy(sending = true),
        actions = HaActionsUi(loading = true),
    )

    @Composable private fun Screen(ui: HaUi, picker: Boolean = false) = HaScreenContent(ui, pickerOpen = picker)

    @Test fun notSetUpLight() = snap("ha_not_set_up", false) { Screen(HaUi()) }
    @Test fun notSetUpDark() = snap("ha_not_set_up", true) { Screen(HaUi()) }
    @Test fun workingLight() = snap("ha_working", false) { Screen(working) }
    @Test fun workingDark() = snap("ha_working", true) { Screen(working) }
    @Test fun errorLight() = snap("ha_error", false) { Screen(failing) }
    @Test fun errorDark() = snap("ha_error", true) { Screen(failing) }
    @Test fun emptyLight() = snap("ha_empty", false) { Screen(emptyWatch.copy(find = HaFindUi.Done(emptyList()))) }
    @Test fun emptyDark() = snap("ha_empty", true) { Screen(emptyWatch.copy(find = HaFindUi.Done(emptyList()))) }
    @Test fun loadingLight() = snap("ha_loading", false) { Screen(loading) }
    @Test fun loadingDark() = snap("ha_loading", true) { Screen(loading) }
    @Test fun pickerOpenLight() = snap("ha_picker", false) { Screen(working.copy(actions = working.actions.copy(chosen = null)), picker = true) }
    @Test fun pickerOpenDark() = snap("ha_picker", true) { Screen(working.copy(actions = working.actions.copy(chosen = null)), picker = true) }
}
