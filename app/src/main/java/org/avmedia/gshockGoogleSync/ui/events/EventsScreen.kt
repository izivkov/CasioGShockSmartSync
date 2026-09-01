package org.avmedia.gshockGoogleSync.ui.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import org.avmedia.gshockGoogleSync.ui.common.AppSwitchWithText
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ItemView
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.model.Event

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
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, spacer, eventsList, manualToggle, buttonsRow) = createRefs()

                ScreenTitle(
                    text = stringResource(id = R.string.events),
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top)
                        bottom.linkTo(spacer.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                )

                Spacer(
                    modifier = Modifier
                        .height(16.dp)
                        .constrainAs(spacer) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(eventsList.top)
                        }
                )

                Column(
                    modifier = Modifier
                        .constrainAs(eventsList) {
                            top.linkTo(spacer.bottom)
                            bottom.linkTo(manualToggle.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            height = Dimension.fillToConstraints
                        }
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
                        .padding(end = 8.dp)
                        .constrainAs(manualToggle) {
                            top.linkTo(eventsList.bottom)
                            bottom.linkTo(buttonsRow.top)
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AppSwitchWithText(
                        isChecked = isManualMode,
                        onCheckedChange = { viewModel.toggleManualMode(it) },
                        modifier = Modifier,
                        text = stringResource(id = R.string.manual)
                    )
                }

                val buttons = arrayListOf(
                    ButtonData(
                        text = stringResource(id = R.string.send_events_to_watch),
                        onClick = { viewModel.sendEventsToWatch() }
                    )
                )
                
                ButtonsRow(
                    buttons = buttons,
                    modifier = Modifier.constrainAs(buttonsRow) {
                        top.linkTo(manualToggle.bottom)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
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

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val enabledCount = events.count { it.enabled }
        events.forEachIndexed { index, event ->
            ItemView {
                EventItem(
                    title = event.title,
                    period = event.getPeriodFormatted(),
                    frequency = event.getFrequencyFormatted(),
                    enabled = event.enabled,
                    onEnabledChange = { newValue ->
                        eventViewModel.toggleEvents(index, newValue)
                    },
                    enabledCount = enabledCount,
                    onEdit = if (onEdit != null) { { onEdit(index) } } else null
                )
            }
        }
    }
}
