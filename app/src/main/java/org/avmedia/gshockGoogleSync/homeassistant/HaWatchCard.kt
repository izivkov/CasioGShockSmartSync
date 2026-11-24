package org.avmedia.gshockGoogleSync.homeassistant

import AppSwitch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton

/** Card 2: how this watch shows up in Home Assistant. */
@Composable
internal fun HaWatchCard(
    saved: Boolean,
    watch: HaWatchUi,
    deviceName: String,
    model: String,
    onName: (String) -> Unit,
    onModel: (String) -> Unit,
    changed: Boolean,
    onSave: () -> Unit,
    onRaw: (Boolean) -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    val context = LocalContext.current
    HaCard("This watch", R.drawable.ic_mdi_watch) {
        if (!watch.registered) {
            // The first registration fixes the name for good (notify action and entity ids): ask before it.
            OutlinedTextField(
                value = deviceName, onValueChange = onName, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Watch name in Home Assistant") },
                supportingText = { Text("Use the full model, for example GBX-H5600KI-5ER") },
            )
            HaNote("Sets the device, sensor and notify names in Home Assistant. It cannot be changed later.")
        } else OutlinedTextField(
            value = deviceName, onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Name in Home Assistant") },
            supportingText = { Text("fixed at registration") },
        )
        OutlinedTextField(
            value = model, onValueChange = onModel, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Model (empty: from the watch name)") },
        )
        if (!saved) {
            HaNote("Set up the connection first.")
            return@HaCard
        }
        if (!watch.registered) {
            when {
                watch.suggestedName.isBlank() && watch.confirmedName == null -> HaNote("Connect your watch to name it and register it.")
                watch.confirmedName == deviceName.trim() -> HaNote("Name confirmed: ${watch.confirmedName}. The watch registers when it is connected.")
                else -> AppButton("Confirm name and register", onConfirm, enabled = deviceName.isNotBlank(), modifier = Modifier.fillMaxWidth())
            }
        } else if (changed) AppButton("Save name and model", onSave, modifier = Modifier.fillMaxWidth())
        if (watch.registered) {
            HaNote("Registered: yes")
            HaNote("Firmware: " + (watch.firmware ?: "not known yet"))
        }
        if (!watch.notificationsSupported) HaNote("This watch cannot show notifications")
        if (watch.others.isNotEmpty()) HaNote("Other watches in Home Assistant: " + watch.others.joinToString() + ". Each keeps its own device; its values are sent while it is connected.")
        watch.lastUpdate?.let { HaNote("Last update: $it") }
        if (watch.failed) HaNote("The last update did not go through. It is tried again at the next connection.", error = true)
        if (watch.lookingUpDevice) HaLoading("Looking for the device in Home Assistant...")
        AppButton("Open in Home Assistant", { openInBrowser(context, watch.deviceUrl) }, enabled = watch.deviceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth())
        HaDetails(watch.lastText) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HaNote("Read raw watch settings (for decoding)")
                    HaNote(watch.rawUntil?.let { "on until $it" } ?: "off")
                }
                AppSwitch(checked = watch.rawUntil != null, onCheckedChange = onRaw)
            }
        }
    }
}
