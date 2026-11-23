package org.avmedia.gshockGoogleSync.homeassistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.avmedia.gshockGoogleSync.ui.common.AppButton

/** The "Dashboard" card: [status] is what is at the dashboard address, [message] the last result (an error when [failed]). */
data class HaDashUi(
    val status: DashStatus = DashStatus.None,
    val working: Boolean = false,
    val message: String = "",
    val failed: Boolean = false,
    val url: String = "",
)

/** The button text for each state. */
internal fun dashButtonText(status: DashStatus): String =
    if (status is DashStatus.Ours) "Update dashboard" else "Create dashboard in Home Assistant"

/** Card 5: makes or updates the watch dashboard in Home Assistant; the YAML copy is the way without admin rights. */
@Composable
internal fun HaDashboardCard(
    saved: Boolean,
    registered: Boolean,
    ui: HaDashUi,
    onCreate: () -> Unit,
    onCopyYaml: () -> Unit,
) {
    val context = LocalContext.current
    HaCard("Dashboard") {
        when {
            !saved -> HaNote("Set up the connection first.")
            !registered -> HaNote("Connect your watch first. Its sensors must exist in Home Assistant.")
            else -> {
                AppButton(
                    dashButtonText(ui.status), onCreate,
                    enabled = !ui.working && ui.status != DashStatus.Foreign, modifier = Modifier.fillMaxWidth(),
                )
                when {
                    ui.working -> HaLoading("Talking to Home Assistant...")
                    ui.status == DashStatus.Foreign ->
                        HaNote("A dashboard with this address exists that this app did not make. It is never changed.", error = true)
                    ui.message.isNotBlank() -> HaNote(ui.message, error = ui.failed)
                    ui.status == DashStatus.Ours(outdated = true) -> HaNote("The watch has new sensors since the dashboard was made.")
                    ui.status == DashStatus.Ours(outdated = false) -> HaNote("The dashboard is up to date.")
                }
                if (!ui.failed && ui.url.isNotBlank()) {
                    AppButton("Open dashboard", { openInBrowser(context, ui.url) }, modifier = Modifier.fillMaxWidth())
                }
                AppButton("Copy YAML", onCopyYaml, enabled = !ui.working, modifier = Modifier.fillMaxWidth())
                HaNote("Paste the YAML into a new dashboard: Edit, three dots, Raw configuration editor.")
            }
        }
    }
}
