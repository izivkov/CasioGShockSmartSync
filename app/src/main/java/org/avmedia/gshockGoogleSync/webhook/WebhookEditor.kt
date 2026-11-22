package org.avmedia.gshockGoogleSync.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import AppText
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

/** The editor of one webhook: presets on top, then Where, When and What. */
@Composable
internal fun WebhookEditor(
    initial: Webhook,
    isNew: Boolean,
    variables: List<Variable>,
    viewModel: WebhooksViewModel,
    onClose: () -> Unit,
    pickerOpen: Boolean = false,
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
    val testState by viewModel.testState.collectAsState()
    val values by viewModel.previewValues.collectAsState()
    val storedHooks by viewModel.hooks.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadPreviewValues() }

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

    val baseline = storedHooks.firstOrNull { it.id == initial.id } ?: Webhook.newDefault().copy(id = initial.id)
    val dirty = current() != baseline

    // Keep what is typed, so it survives leaving the screen or the app being killed.
    val draftNow = current()
    LaunchedEffect(draftNow) {
        if (draftNow != initial) viewModel.saveDraft(draftNow)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            text = stringResource(if (isNew) R.string.webhook_new else R.string.webhook_edit),
            modifier = Modifier,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(text = "Start from", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            PresetChips { p ->
                contentType = p.contentType
                method = p.method
                body = TextFieldValue(p.bodyTemplate)
                p.onWatchAction?.let { onWatchAction = it }
                p.onSync?.let { onSync = it }
                urlHint = p.urlHint
            }
            WhereCard(name, { name = it }, url, { url = it }, urlHint, method, { method = it }, headers)
            WhenCard(
                onWatchAction, { onWatchAction = it }, onSync, { onSync = it }, onHealth, { onHealth = it },
                interval, { interval = it }, enabled, { enabled = it },
            )
            HaCard("What") {
                OutlinedTextField(
                    value = contentType, onValueChange = { contentType = it }, singleLine = true,
                    label = { Text("Content type") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text("Message (not sent with GET)") },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                VariablePicker(variables, onInsert = { body = insertAtCursor(body, it) }, startOpen = pickerOpen)
                AppText(text = "Preview", fontSize = 14.sp)
                val preview = BodyPreview.render(body.text, TemplateRenderer.isJsonContentType(contentType), variables.map { it.name }.toSet(), values)
                AppText(text = preview.ifBlank { "(empty)" }, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                HaNote("Values <like this> have no value yet; the watch ones appear when it is connected.")
                AppButton(text = "Test", onClick = { viewModel.test(current()) }, modifier = Modifier.fillMaxWidth())
                TestResultView(testState)
            }
        }
        if (dirty) {
            AppText(text = "Unsaved changes", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        ButtonsRow(
            buttons = buildList {
                add(ButtonData("Cancel", onClose))
                if (!isNew) add(ButtonData("Delete", { viewModel.delete(initial.id); onClose() }))
                add(ButtonData("Save", {
                    if (viewModel.save(current())) onClose()
                    else AppSnackbar("Saved webhooks could not be read: confirm on the list screen first")
                }))
            }
        )
    }
}
