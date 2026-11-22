package org.avmedia.gshockGoogleSync.webhook

import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.R

/** Card 4: what a press of the watch button starts in Home Assistant. */
@Composable
internal fun HaButtonCard(
    saved: Boolean,
    actions: HaActionsUi,
    onChoose: (HaAction?) -> Unit,
    onRefresh: () -> Unit,
    pickerOpen: Boolean = false,
) {
    HaCard("Watch button", R.drawable.find_phone) {
        when {
            !saved -> HaNote("Set up the connection first.")
            else -> {
                HaActionPicker(actions.items, actions.chosen, onChoose, onRefresh, pickerOpen)
                when {
                    actions.loading -> HaLoading("Loading your automations, scripts and scenes...")
                    actions.error != null -> HaNote(actions.error, error = true)
                    actions.items.isEmpty() -> HaNote("Home Assistant has no automations, scripts or scenes to pick.")
                    actions.lastRun.isNotBlank() -> HaNote(actions.lastRun)
                }
            }
        }
    }
}
