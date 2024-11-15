package org.avmedia.gShockSmartSyncCompose.ui.events

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.applicationContext
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.theme.GShockSmartSyncTheme
import org.avmedia.gShockSmartSyncCompose.ui.common.ButtonData
import org.avmedia.gShockSmartSyncCompose.ui.common.ButtonsRow
import org.avmedia.gShockSmartSyncCompose.ui.common.ItemList
import org.avmedia.gShockSmartSyncCompose.ui.common.ItemView
import org.avmedia.gShockSmartSyncCompose.ui.common.ScreenTitle
import org.avmedia.gshockapi.Event

@Composable
fun EventsScreen(viewModel: EventViewModel = viewModel()) {

    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, events, buttonsRow) = createRefs()

                ScreenTitle(stringResource(id = R.string.events), Modifier
                    .constrainAs(title) {
                        top.linkTo(parent.top)
                        bottom.linkTo(events.top)
                    })

                Column(
                    modifier = Modifier
                        .constrainAs(events) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(buttonsRow.top)
                            height = Dimension.fillToConstraints
                        }
                        .verticalScroll(rememberScrollState())  // Make content scrollable
                        .padding(0.dp)
                        .fillMaxWidth()
                        .fillMaxSize()
                ) {
                    EventList()
                }

                Column(modifier = Modifier
                    .constrainAs(buttonsRow) {
                        top.linkTo(events.bottom)  // Link top of buttonsRow to bottom of content
                        bottom.linkTo(parent.bottom)  // Keep buttons at the bottom
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                    .fillMaxWidth()
                ) {
                    val buttons = arrayListOf(
                        ButtonData(
                            text = stringResource(id = R.string.send_events_to_watch),
                            onClick = { viewModel.sendEventsToWatch() }
                        )
                    )
                    ButtonsRow(buttons = buttons)
                }
            }
        }
    }
}

@Composable
fun EventList(eventViewModel: EventViewModel = viewModel()) {

    val events by eventViewModel.events.collectAsState()

    LaunchedEffect(Unit) {
        eventViewModel.loadEvents(applicationContext())
    }

    @Composable
    fun createEvent(): List<Any> {
        val eventItems = mutableListOf<Any>()
        val enabledCount = events.count { it.enabled } // Count how many items are enabled

        events.forEachIndexed { index: Int, event: Event ->
            ItemView {
                EventItem(
                    title = event.title,
                    period = event.getPeriodFormatted(),
                    frequency = event.getFrequencyFormatted(),
                    enabled = event.enabled,
                    onEnabledChange = { newValue ->
                        eventViewModel.toggleEvents(index, newValue)
                    },
                    enabledCount = enabledCount
                )
            }
        }

        return eventItems.toList()
    }

    Column(
        modifier = Modifier
    ) {
        ItemList(createEvent())
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewEventsScreen() {
    EventsScreen()
}