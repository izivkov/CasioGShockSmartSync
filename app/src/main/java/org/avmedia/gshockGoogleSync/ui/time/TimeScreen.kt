package org.avmedia.gshockGoogleSync.ui.time

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun TimeScreen(timeViewModel: TimeViewModel = hiltViewModel()) {

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(
                    stringResource(id = R.string.time),
                    Modifier
                )

                LocalTimeView(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp)
                )

                val watchFeatureManager = LocalWatchFeatureManager.current
                val isStepCounterSupported =
                    watchFeatureManager.isCardSupported("step_counter_card")

                TimerView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                )

                if (isStepCounterSupported) {
                    StepCounterView(
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }

                WatchNameView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                )

                WatchInfoView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                )

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTimeScreen() {
    TimeScreen()
}
