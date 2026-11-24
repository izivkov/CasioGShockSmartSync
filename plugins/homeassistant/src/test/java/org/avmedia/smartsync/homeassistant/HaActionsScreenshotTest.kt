package org.avmedia.smartsync.homeassistant

import org.junit.Test

/** The Home Assistant part of its Actions card (the app draws the name and switch above it) and the action picker. */
class HaActionsScreenshotTest : ScreenshotTest() {
    private val goodnight = HaAction("script", "script.goodnight_and_all_lights_off_downstairs", "Goodnight and all lights off downstairs")
    private val items = listOf(
        HaAction("automation", "automation.front_door", "Front door camera"), goodnight,
        HaAction("scene", "scene.movie", "Movie night"), HaAction("script", "script.find_keys", "Find keys"),
    )

    @Test fun actionsLineLight() = snap("ha_actions_line", dark = false) { HaButtonActionContent(true, HaActionsUi(items = items, chosen = goodnight), {}, {}, {}) }
    @Test fun actionsLineNotSetUpLight() = snap("ha_actions_line_not_set_up", dark = false) { HaButtonActionContent(false, HaActionsUi(), {}, {}, {}) }
    @Test fun pickerLight() = snap("button_action_picker", dark = false) { HaActionPickerContent(HaActionsUi(items = items, chosen = goodnight), {}, {}, {}) }
    @Test fun pickerDark() = snap("button_action_picker", dark = true) { HaActionPickerContent(HaActionsUi(items = items, chosen = goodnight), {}, {}, {}) }
    @Test fun pickerSearchLight() = snap("button_action_picker_search", dark = false) {
        HaActionPickerContent(HaActionsUi(items = items, chosen = goodnight), {}, {}, {}, initialQuery = "night")
    }
}
