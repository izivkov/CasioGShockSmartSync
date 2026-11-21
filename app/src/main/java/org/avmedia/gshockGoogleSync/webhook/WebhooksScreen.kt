package org.avmedia.gshockGoogleSync.webhook

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.R
import androidx.compose.ui.res.stringResource
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import AppSwitch
import AppText
import AppTextLarge
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun WebhooksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebhooksViewModel = hiltViewModel(),
) {
    val hooks by viewModel.hooks.collectAsState()
    val results by viewModel.results.collectAsState()
    var editing by remember { mutableStateOf<Webhook?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshResults()
            delay(3_000)
        }
    }

    GShockSmartSyncTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = editing
            if (current == null) {
                BackHandler(onBack = onBack)
                Column(Modifier.fillMaxSize()) {
                    ScreenTitle(text = stringResource(R.string.webhooks), modifier = Modifier)
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (hooks.isEmpty()) {
                            AppText(
                                text = stringResource(R.string.webhooks_empty),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        hooks.forEach { hook ->
                            WebhookRow(
                                hook = hook,
                                result = results[hook.id],
                                onOpen = { editing = hook },
                                onEnabled = { viewModel.setEnabled(hook.id, it) },
                            )
                        }
                    }
                    ButtonsRow(
                        buttons = listOf(
                            ButtonData(stringResource(R.string.webhook_back), onBack),
                            ButtonData(stringResource(R.string.webhook_add), { editing = Webhook.newDefault() }),
                        )
                    )
                }
            } else {
                BackHandler {
                    viewModel.clearTestReport()
                    editing = null
                }
                WebhookEditor(
                    initial = current,
                    isNew = hooks.none { it.id == current.id },
                    variables = viewModel.variables,
                    viewModel = viewModel,
                    onClose = {
                        viewModel.clearTestReport()
                        editing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun WebhookRow(
    hook: Webhook,
    result: WebhookResult?,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppTextLarge(text = hook.name.ifBlank { "(no name)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppText(
                    text = "${hook.method} ${hostOf(hook.url)}",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(text = describeResult(result), fontSize = 14.sp)
            }
            AppSwitch(checked = hook.enabled, onCheckedChange = onEnabled)
        }
    }
}

/** Only the host: the URL may carry a token in its path or query. */
private fun hostOf(url: String): String =
    url.substringAfter("://", url).substringBefore('/').substringBefore('?').ifBlank { "(no URL)" }

private fun describeResult(result: WebhookResult?): String {
    if (result == null) return "Not sent yet"
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.timeMillis))
    val what = when {
        result.ok -> "OK ${result.status}"
        result.retrying -> "Failed (${result.status ?: "no response"}), retrying"
        else -> "Failed (${result.status ?: result.message.take(40)})"
    }
    return "$what at $time"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebhookEditor(
    initial: Webhook,
    isNew: Boolean,
    variables: List<Variable>,
    viewModel: WebhooksViewModel,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var urlHint by remember { mutableStateOf<String?>(null) }
    var method by remember { mutableStateOf(initial.method) }
    var contentType by remember { mutableStateOf(initial.contentType) }
    var body by remember { mutableStateOf(TextFieldValue(initial.bodyTemplate)) }
    val headers = remember { mutableStateListOf<WebhookHeader>().apply { addAll(initial.headers) } }
    var onWatchAction by remember { mutableStateOf(initial.onWatchAction) }
    var onSync by remember { mutableStateOf(initial.onSync) }
    var onHealth by remember { mutableStateOf(initial.onHealthData) }
    var interval by remember { mutableStateOf(initial.minIntervalMinutes.toString()) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var showVariables by remember { mutableStateOf(false) }
    val testReport by viewModel.testReport.collectAsState()

    fun current() = initial.copy(
        name = name.trim(),
        url = url.trim(),
        method = method,
        headers = headers.filter { it.key.isNotBlank() }.toList(),
        contentType = contentType.trim(),
        bodyTemplate = body.text,
        onWatchAction = onWatchAction,
        onSync = onSync,
        onHealthData = onHealth,
        minIntervalMinutes = interval.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        enabled = enabled,
    )

    fun insert(text: String) {
        val sel = body.selection
        val newText = body.text.replaceRange(sel.min, sel.max, text)
        body = TextFieldValue(newText, TextRange(sel.min + text.length))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            text = stringResource(if (isNew) R.string.webhook_new else R.string.webhook_edit),
            modifier = Modifier,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL (https://...)") },
                placeholder = urlHint?.let { h -> { Text(h) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(text = "Method")
                Webhook.METHODS.forEach { m ->
                    FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                }
            }

            AppText(text = "Headers")
            headers.forEachIndexed { index, header ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = header.key, onValueChange = { headers[index] = header.copy(key = it) },
                        singleLine = true, label = { Text("Key") }, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = header.value, onValueChange = { headers[index] = header.copy(value = it) },
                        singleLine = true, label = { Text("Value") }, modifier = Modifier.weight(1.4f),
                    )
                    TextButton(onClick = { headers.removeAt(index) }) { Text("X") }
                }
            }
            TextButton(onClick = { headers.add(WebhookHeader()) }) { Text("Add header") }

            OutlinedTextField(
                value = contentType, onValueChange = { contentType = it }, singleLine = true,
                label = { Text("Content type") }, modifier = Modifier.fillMaxWidth(),
            )

            AppText(text = "Presets")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WebhookPresets.ALL.forEach { p ->
                    AssistChip(
                        onClick = {
                            contentType = p.contentType
                            method = p.method
                            body = TextFieldValue(p.bodyTemplate)
                            p.onWatchAction?.let { onWatchAction = it }
                            p.onSync?.let { onSync = it }
                            urlHint = p.urlHint
                        },
                        label = { Text(p.label) },
                    )
                }
            }

            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text("Body template (not sent with GET)") },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = { showVariables = !showVariables }) {
                Text(if (showVariables) "Hide variables" else "Show variables (tap one to insert)")
            }
            if (showVariables) {
                AppText(
                    text = "{{all}}  every available variable as one JSON object",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { insert("{{all}}") }
                        .padding(vertical = 4.dp),
                    fontSize = 14.sp,
                )
                variables.forEach { v ->
                    AppText(
                        text = "{{${v.name}}}  ${v.description}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { insert("{{${v.name}}}") }
                            .padding(vertical = 4.dp),
                        fontSize = 14.sp,
                    )
                }
            }

            AppText(text = "When to send")
            SwitchLine("A watch action runs (button press, Phone Finder)", onWatchAction) { onWatchAction = it }
            SwitchLine("After each watch connection / sync", onSync) { onSync = it }
            SwitchLine("Health data downloaded (not available yet)", onHealth, enabled = false) { onHealth = it }
            OutlinedTextField(
                value = interval, onValueChange = { interval = it.filter { c -> c.isDigit() }.take(5) },
                singleLine = true, label = { Text("Minimum minutes between sends (0 = no limit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchLine("Enabled", enabled) { enabled = it }

            AppButton(text = "Test", onClick = { viewModel.test(current()) }, modifier = Modifier.fillMaxWidth())
            testReport?.let { AppText(text = it, fontSize = 14.sp) }
        }
        ButtonsRow(
            buttons = buildList {
                add(ButtonData("Cancel", onClose))
                if (!isNew) add(ButtonData("Delete", { viewModel.delete(initial.id); onClose() }))
                add(ButtonData("Save", { viewModel.save(current()); onClose() }))
            }
        )
    }
}

@Composable
private fun SwitchLine(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(text = text, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
