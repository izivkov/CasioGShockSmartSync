package org.avmedia.gshockGoogleSync.ui.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSwitchWithText
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ItemView
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun EventsScreen(viewModel: EventViewModel = hiltViewModel()) {
    GShockSmartSyncTheme {
        val isManualMode by viewModel.isManualMode.collectAsState()
        var editingEventIndex by remember { mutableStateOf<Int?>(null) }
        val events by viewModel.events.collectAsState()

        if (editingEventIndex != null) {
            ReminderEditDialog(
                event = events[editingEventIndex!!],
                onDismiss = { editingEventIndex = null },
                onSave = { updatedEvent ->
                    viewModel.updateEvent(editingEventIndex!!, updatedEvent)
                    editingEventIndex = null
                }
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(text = stringResource(id = R.string.events), modifier = Modifier)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    EventList(
                        onEdit = if (isManualMode) { index -> editingEventIndex = index } else null
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AppSwitchWithText(
                        isChecked = isManualMode,
                        onCheckedChange = viewModel::toggleManualMode,
                        modifier = Modifier,
                        text = stringResource(id = R.string.manual)
                    )
                }

                ButtonsRow(
                    buttons = listOf(
                        ButtonData(
                            text = stringResource(id = R.string.send_events_to_watch),
                            onClick = viewModel::sendEventsToWatch
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun EventList(
    eventViewModel: EventViewModel = hiltViewModel(),
    onEdit: ((Int) -> Unit)? = null
) {
    val events by eventViewModel.events.collectAsState()

    LaunchedEffect(Unit) {
        eventViewModel.loadEvents()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val enabledCount = events.count { it.enabled }
        events.forEachIndexed { index, event ->
            ItemView {
                EventItem(
                    title = event.title,
                    period = event.getPeriodFormatted(),
                    frequency = event.getFrequencyFormatted(),
                    enabled = event.enabled,
                    onEnabledChange = { newValue -> eventViewModel.toggleEvents(index, newValue) },
                    enabledCount = enabledCount,
                    onEdit = onEdit?.let { { it(index) } }
                )
            }
        }
    }
}
