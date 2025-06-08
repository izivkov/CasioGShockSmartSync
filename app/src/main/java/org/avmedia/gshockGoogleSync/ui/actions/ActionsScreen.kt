package org.avmedia.gshockGoogleSync.ui.actions

import PhoneView
import PhotoView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ItemList
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.WatchInfo

@Composable
fun ActionsScreen(
    modifier: Modifier = Modifier,
    actionsViewModel: ActionsViewModel = hiltViewModel(),
) {
    GShockSmartSyncTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, actions) = createRefs()

                ScreenTitle(
                    text = stringResource(id = R.string.actions),
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top)
                        bottom.linkTo(actions.top)
                    }
                )

                ActionsContent(
                    modifier = Modifier.constrainAs(actions) {
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
private fun ActionsContent(
    modifier: Modifier = Modifier,
    actionsViewModel: ActionsViewModel
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(0.dp)
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        ItemList(
            items = createActionItems(actionsViewModel::updateAction)
        )
    }
}

@Composable
private fun createActionItems(onUpdateAction: (ActionsViewModel.Action) -> Unit): List<Any> =
    listOfNotNull(
        if (WatchInfo.findButtonUserDefined) PhoneFinderView(onUpdateAction) else null,
        SetTimeView(onUpdateAction),
        if (WatchInfo.hasReminders) RemindersView(onUpdateAction) else null,
        PhotoView(onUpdateAction),
        FlashlightView(onUpdateAction),
        VoiceAssistView(onUpdateAction),
        SkipToNextTrackView(onUpdateAction),
        PrayerAlarmsView(onUpdateAction),
        SeparatorView(),
        PhoneView(onUpdateAction)
    )

@Preview(showBackground = true)
@Composable
private fun ActionsScreenPreview() {
    ActionsScreen()
}
