package org.avmedia.gshockGoogleSync.webhook

import androidx.compose.foundation.layout.fillMaxWidth
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
) {
    val context = LocalContext.current
    HaCard("This watch", R.drawable.ic_watch_later_black_24dp) {
        OutlinedTextField(
            value = deviceName, onValueChange = onName, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Name in Home Assistant (empty: the watch name)") },
        )
        OutlinedTextField(
            value = model, onValueChange = onModel, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Model (empty: from the watch name)") },
        )
        if (!saved) {
            HaNote("Set up the connection first.")
            return@HaCard
        }
        if (changed) AppButton("Save name and model", onSave, modifier = Modifier.fillMaxWidth())
        if (!watch.registered) HaNote("Not registered yet. Connect your watch and it appears in Home Assistant.")
        else {
            HaNote("Registered: yes")
            HaNote("Firmware: " + (watch.firmware ?: "not known yet"))
        }
        watch.lastUpdate?.let { HaNote("Last update: $it") }
        if (watch.failed) HaNote("The last update did not go through. It is tried again at the next connection.", error = true)
        if (watch.lookingUpDevice) HaLoading("Looking for the device in Home Assistant...")
        AppButton("Open in Home Assistant", { openInBrowser(context, watch.deviceUrl) }, enabled = watch.deviceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth())
        HaDetails(watch.lastText)
    }
}
