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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ItemList
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockGoogleSync.R

@Composable
fun ActionsScreen() {
    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, actions) = createRefs()

                ScreenTitle(stringResource(id = R.string.actions), Modifier
                    .constrainAs(title) {
                        top.linkTo(parent.top)  // Link top of content to parent top
                        bottom.linkTo(actions.top)  // Link bottom of content to top of buttonsRow
                    })

                Column(
                    modifier = Modifier
                        .constrainAs(actions) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(parent.bottom)
                            height = Dimension.fillToConstraints
                        }
                        .verticalScroll(rememberScrollState())  // Make content scrollable
                        .padding(0.dp)
                        .fillMaxWidth()
                        .fillMaxSize()
                ) {
                    ActionList()
                }
            }
        }
    }
}

@Composable
fun ActionList() {

    @Composable
    fun createActions(): List<Any> {
        val actionsViewModel: ActionsViewModel = hiltViewModel()

        return listOfNotNull(
            if (WatchInfo.findButtonUserDefined) PhoneFinderView(actionsViewModel::updateAction) else null,
            SetTimeView(actionsViewModel::updateAction),
            if (WatchInfo.hasReminders) RemindersView(actionsViewModel::updateAction) else null,
            PhotoView(actionsViewModel::updateAction),
            FlashlightView(actionsViewModel::updateAction),
            VoiceAssistView(actionsViewModel::updateAction),
            SkipToNextTrackView(actionsViewModel::updateAction),
            PrayerAlarmsView(actionsViewModel::updateAction),
            SeparatorView(),
            PhoneView(actionsViewModel::updateAction),
        )
    }

    Column(
        modifier = Modifier
    ) {
        ItemList(createActions())
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewActionsScreen() {
    ActionsScreen()
}

