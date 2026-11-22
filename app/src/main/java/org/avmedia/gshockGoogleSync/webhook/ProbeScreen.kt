package org.avmedia.gshockGoogleSync.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import AppSwitch
import AppText
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

/**
 * Debug screen: asks the connected watch for Casio read-request codes and lists the raw answers.
 * It keeps going across reconnects (an H5600 drops about every 45 s).
 */
@Composable
fun ProbeScreen(viewModel: WebhooksViewModel, onBack: () -> Unit) {
    val probe = viewModel.probe
    val entries by probe.entries.collectAsState()
    val status by probe.status.collectAsState()
    val running by probe.running.collectAsState()
    var all by remember { mutableStateOf(false) }
    val result = probe.result()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(text = "Probe watch", modifier = Modifier)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(text = "Watch: ${result.watchName.ifBlank { "(none)" }}", fontSize = 14.sp)
            AppText(text = "Phone time: ${result.phoneTime}", fontSize = 14.sp)
            AppText(text = status, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(text = "Also every other code 0x01 to 0x4F", modifier = Modifier.weight(1f), fontSize = 14.sp)
                AppSwitch(checked = all, onCheckedChange = { all = it }, enabled = !running)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            itemsIndexed(entries) { _, e ->
                val text = when {
                    !e.done -> "${ProbePlan.hex(e.code)}  ..."
                    e.answers.orEmpty().isEmpty() -> "${ProbePlan.hex(e.code)}  no answer"
                    else -> "${ProbePlan.hex(e.code)}  " + e.answers.orEmpty().joinToString("  |  ")
                }
                AppText(text = text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        ButtonsRow(
            buttons = listOf(
                ButtonData(if (running) "Stop" else if (entries.any { it.done }) "Continue" else "Start", {
                    if (running) probe.stop() else probe.start(all)
                }),
                ButtonData("Clear", { probe.clear() }, enabled = entries.isNotEmpty()),
                ButtonData("Send to webhooks", {
                    val n = viewModel.sendProbeToWebhooks()
                    AppSnackbar(if (n == 0) "No enabled POST/PUT webhook to send to" else "Probe result queued for $n webhook(s)")
                }, enabled = entries.any { it.done }),
                ButtonData("Back", onBack),
            )
        )
    }
}
