package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ItemList
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import timber.log.Timber

@Composable
fun ActionsScreen(
    modifier: Modifier = Modifier,
    actionsViewModel: ActionsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    DisposableEffect(Unit) {
        onDispose {
            Timber.i("Screen is now out of view. Saving state...")
            actionsViewModel.save()
        }
    }

    GShockSmartSyncTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (title, actions) = createRefs()

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
                            bottom.linkTo(parent.bottom)
                            height = Dimension.fillToConstraints
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
            modifier
                .verticalScroll(rememberScrollState())
                .padding(0.dp)
                .fillMaxWidth()
                .fillMaxSize()
    ) { ItemList(items = createActionItems(actionsViewModel)) }
}

@Composable
private fun createActionItems(actionsViewModel: ActionsViewModel): List<Any> {
    val watchFeatureManager = LocalWatchFeatureManager.current

    return listOfNotNull(
        if (watchFeatureManager.isFeatureSupported("actions.find_phone"))
            ToggleActionView(
                ActionContainer.FindPhoneAction::class.java,
                R.string.find_phone, R.drawable.find_phone, actionsViewModel
            )
        else null,
        ToggleActionView(
            ActionContainer.SetTimeAction::class.java,
            R.string.set_time, R.drawable.ic_watch_later_black_24dp, actionsViewModel
        ),
        if (watchFeatureManager.isFeatureSupported("actions.reminders"))
            ToggleActionView(
                ActionContainer.SetEventsAction::class.java,
                R.string.set_reminders, R.drawable.events, actionsViewModel
            )
        else null,
        PhotoView(onUpdate = { actionsViewModel.updateAction(it) }, actionsViewModel),
        ToggleActionView(
            ActionContainer.ToggleFlashlightAction::class.java,
            R.string.toggle_flashlight, R.drawable.flashlight, actionsViewModel
        ),
        ToggleActionView(
            ActionContainer.StartVoiceAssistAction::class.java,
            R.string.start_voice_assistant, R.drawable.voice_assist, actionsViewModel
        ),
        ToggleActionView(
            ActionContainer.NextTrack::class.java,
            R.string.next_track, R.drawable.skip_next, actionsViewModel,
            infoRes = R.string.skip_to_next_track_info
        ),
        ToggleActionView(
            ActionContainer.TogglePlayPauseAction::class.java,
            R.string.play_pause, R.drawable.play_pause, actionsViewModel,
            infoRes = R.string.play_pause_info
        ),
        ToggleActionView(
            ActionContainer.PrayerAlarmsAction::class.java,
            R.string.set_prayer_alarms, R.drawable.prayer_times, actionsViewModel,
            infoRes = R.string.prayer_times_info
        ),
        SeparatorView(),
        PhoneView(onUpdate = { actionsViewModel.updateAction(it) }, actionsViewModel)
    )
}

@Preview(showBackground = true)
@Composable
private fun ActionsScreenPreview() {
    ActionsScreen()
}
