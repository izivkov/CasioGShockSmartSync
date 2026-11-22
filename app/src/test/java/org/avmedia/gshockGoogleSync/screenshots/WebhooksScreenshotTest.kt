package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.avmedia.gshockGoogleSync.webhook.Variable
import org.avmedia.gshockGoogleSync.webhook.Webhook
import org.avmedia.gshockGoogleSync.webhook.WebhookEditor
import org.avmedia.gshockGoogleSync.webhook.WebhookList
import org.avmedia.gshockGoogleSync.webhook.WebhookResult
import org.avmedia.gshockGoogleSync.webhook.WebhooksViewModel
import org.junit.Test

/** Webhooks list and the webhook editor. */
class WebhooksScreenshotTest : ScreenshotTest() {

    private val hooks = listOf(
        Webhook(id = "1", name = "Home Assistant", url = "https://ha.example.com/api/webhook/abc", onSync = true),
        Webhook(id = "2", name = "TRMNL", url = "https://usetrmnl.com/api/custom_plugins/xyz", enabled = false),
        Webhook(id = "3", name = "", url = ""),
    )
    private val results = mapOf(
        "1" to WebhookResult(timeMillis = 1_700_000_000_000L, ok = true, status = 200),
    )

    @Composable
    private fun ListScreen(list: List<Webhook>) = WebhookList(
        hooks = list, results = if (list.isEmpty()) emptyMap() else results, unreadable = false,
        onBack = {}, onProbe = {}, onSendLog = {}, onAdd = {}, onOpen = {}, onEnabled = { _, _ -> },
        onConfirmOverwrite = {},
    )

    @Composable
    private fun EditorScreen() {
        val vm: WebhooksViewModel = mockk(relaxed = true) {
            every { testReport } returns MutableStateFlow(null)
            every { this@mockk.hooks } returns MutableStateFlow(this@WebhooksScreenshotTest.hooks)
        }
        WebhookEditor(
            initial = hooks[0],
            isNew = false,
            variables = listOf(Variable("event", "What caused the send") { null }),
            viewModel = vm,
            onClose = {},
        )
    }

    @Test fun listLight() = snap("webhooks_list", false) { ListScreen(hooks) }
    @Test fun listDark() = snap("webhooks_list", true) { ListScreen(hooks) }
    @Test fun listEmptyLight() = snap("webhooks_list_empty", false) { ListScreen(emptyList()) }
    @Test fun editorLight() = snap("webhook_editor", false) { EditorScreen() }
    @Test fun editorDark() = snap("webhook_editor", true) { EditorScreen() }
}
