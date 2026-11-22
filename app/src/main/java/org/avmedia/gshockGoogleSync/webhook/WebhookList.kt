package org.avmedia.gshockGoogleSync.webhook

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow

/** The stateless list body of [WebhooksScreen] (also used by the screenshot tests). */
@Composable
internal fun WebhookList(
    hooks: List<Webhook>,
    results: Map<String, WebhookResult>,
    unreadable: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Webhook) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
    onConfirmOverwrite: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        HaTitle(text = stringResource(R.string.webhooks), iconRes = R.drawable.ic_webhook)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (unreadable) {
                HaCard("Saved webhooks") {
                    HaNote("They could not be read. The data is kept untouched, with a raw copy. New webhooks cannot be saved until you confirm.", error = true)
                    AppButton(text = "Start fresh (overwrite)", onClick = onConfirmOverwrite, modifier = Modifier.fillMaxWidth())
                }
            }
            if (hooks.isEmpty()) {
                HaCard("No webhooks yet") { HaNote(stringResource(R.string.webhooks_empty)) }
            }
            hooks.forEach { hook ->
                WebhookCard(hook, results[hook.id], { onOpen(hook) }, { onEnabled(hook.id, it) })
            }
        }
        ButtonsRow(
            buttons = listOf(
                ButtonData(stringResource(R.string.webhook_back), onBack),
                ButtonData(stringResource(R.string.webhook_add), onAdd),
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebhookCard(hook: Webhook, result: WebhookResult?, onOpen: () -> Unit, onEnabled: (Boolean) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppTextLarge(text = hook.name.ifBlank { "(no name)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HaNote(WebhookLabels.hostOf(hook.url))
                }
                AppSwitch(checked = hook.enabled, onCheckedChange = onEnabled)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val triggers = WebhookLabels.triggers(hook)
                if (triggers.isEmpty()) SuggestionChip(onClick = onOpen, label = { Text("No trigger", fontSize = 12.sp) })
                triggers.forEach { SuggestionChip(onClick = onOpen, label = { Text(it, fontSize = 12.sp) }) }
            }
            ResultChip(result, onOpen)
        }
    }
}

/** The last result as a coloured chip: green when it worked, red when it failed, neutral for retrying or never sent. */
@Composable
private fun ResultChip(result: WebhookResult?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        result == null -> scheme.surfaceVariant to scheme.onSurfaceVariant
        result.ok -> scheme.primaryContainer to scheme.onPrimaryContainer
        result.retrying -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.errorContainer to scheme.onErrorContainer
    }
    val time = result?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timeMillis)) }
    AssistChip(
        onClick = onClick,
        label = { Text(WebhookLabels.resultText(result, time), fontSize = 12.sp) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = content),
        border = null,
    )
}
