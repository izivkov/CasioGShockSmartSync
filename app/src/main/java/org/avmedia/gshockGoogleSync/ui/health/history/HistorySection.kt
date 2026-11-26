package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** [HistoryContent] on its own view model: what the Health screen shows below the cards. */
@Composable
fun HistorySection(modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsState()
    HistoryContent(ui, viewModel.events, modifier)
}
