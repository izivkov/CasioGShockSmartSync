package org.avmedia.gshockGoogleSync.ui.actions

import PhoneView
import PhotoView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ItemList
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

@Composable
fun ActionsScreen(
        modifier: Modifier = Modifier,
        actionsViewModel: ActionsViewModel =
                hiltViewModel(LocalContext.current as ComponentActivity),
) {
    LaunchedEffect(Unit) {
        actionsViewModel.uiEvents.collect { event ->
            when (event) {
                is ActionsViewModel.UiEvent.ShowSnackbar -> {
                    AppSnackbar(event.message)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Timber.i("Screen is now out of view. Saving state...")
            actionsViewModel.save()
        }
    }

    GShockSmartSyncTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (title, actions, buttonsRow) = createRefs()

                ScreenTitle(
                        text = stringResource(id = R.string.actions),
                        modifier =
                                Modifier.constrainAs(title) {
                                    top.linkTo(parent.top)
                                    bottom.linkTo(actions.top)
                                }
                )

                ActionsContent(
                        modifier =
                                Modifier.constrainAs(actions) {
                                    top.linkTo(title.bottom)
                                    bottom.linkTo(buttonsRow.top)
                                    height = Dimension.fillToConstraints
                                },
                        actionsViewModel = actionsViewModel
                )

                BottomRow(
                        modifier =
                                Modifier.constrainAs(buttonsRow) {
                                    top.linkTo(actions.bottom)
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                },
                        actionsViewModel = actionsViewModel
                )
            }
        }
    }
}

@Composable
private fun ActionsContent(modifier: Modifier = Modifier, actionsViewModel: ActionsViewModel) {
    Column(
            modifier =
                    modifier.verticalScroll(rememberScrollState())
                            .padding(0.dp)
                            .fillMaxWidth()
                            .fillMaxSize()
    ) { ItemList(items = createActionItems(actionsViewModel)) }
}

@Composable
private fun createActionItems(actionsViewModel: ActionsViewModel): List<Any> {
    // We access the actions list to force recomposition when it changes
    val actions by actionsViewModel.actions.collectAsState()

    return listOfNotNull(
            if (WatchInfo.findButtonUserDefined)
                    PhoneFinderView(actionsViewModel::updateAction, actionsViewModel)
            else null,
            SetTimeView(actionsViewModel::updateAction, actionsViewModel),
            if (WatchInfo.hasReminders)
                    RemindersView(actionsViewModel::updateAction, actionsViewModel)
            else null,
            PhotoView(actionsViewModel::updateAction, actionsViewModel),
            FlashlightView(actionsViewModel::updateAction, actionsViewModel),
            VoiceAssistView(actionsViewModel::updateAction, actionsViewModel),
            SkipToNextTrackView(actionsViewModel::updateAction, actionsViewModel),
            PrayerAlarmsView(actionsViewModel::updateAction, actionsViewModel),
            SeparatorView(),
            PhoneView(actionsViewModel::updateAction, actionsViewModel)
    )
}

@Composable
fun BottomRow(modifier: Modifier, actionsViewModel: ActionsViewModel) {
    Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            val msg = stringResource(id = R.string.actions_saved)
            val buttons =
                    arrayListOf(
                            ButtonData(
                                    text = stringResource(id = R.string.send_to_watch),
                                    onClick = { actionsViewModel.saveWithMessage(msg) }
                            )
                    )

            ButtonsRow(buttons = buttons, modifier = Modifier.weight(2f))

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionsScreenPreview() {
    ActionsScreen()
}
