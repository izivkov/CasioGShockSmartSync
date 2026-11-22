package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** [WorkoutsContent] on its own view model: what the Health screen shows below the history. */
@Composable
fun WorkoutsSection(modifier: Modifier = Modifier) {
    val viewModel: WorkoutsViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsState()
    WorkoutsContent(ui, viewModel.events, modifier)
}
