package org.avmedia.gshockGoogleSync.webhook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme

/** The Webhooks screen: the list, or the editor of one webhook. */
@Composable
fun WebhooksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebhooksViewModel = hiltViewModel(),
) {
    val hooks by viewModel.hooks.collectAsState()
    val results by viewModel.results.collectAsState()
    var editing by remember { mutableStateOf(viewModel.loadDraft()?.hook) }
    val unreadable by viewModel.unreadable.collectAsState()

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
                WebhookList(
                    hooks = hooks,
                    results = results,
                    unreadable = unreadable,
                    onBack = onBack,
                    onAdd = { editing = Webhook.newDefault() },
                    onOpen = { editing = it },
                    onEnabled = { id, on -> viewModel.setEnabled(id, on) },
                    onConfirmOverwrite = { viewModel.confirmOverwrite() },
                )
            } else {
                val closeEditor = {
                    viewModel.clearDraft() // Save or Cancel ends the draft
                    viewModel.clearTestReport()
                    editing = null
                }
                BackHandler(onBack = closeEditor)
                WebhookEditor(
                    initial = current,
                    isNew = hooks.none { it.id == current.id },
                    variables = viewModel.variables,
                    viewModel = viewModel,
                    onClose = closeEditor,
                )
            }
        }
    }
}
