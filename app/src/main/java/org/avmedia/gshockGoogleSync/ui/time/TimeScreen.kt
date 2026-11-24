package org.avmedia.gshockGoogleSync.ui.time

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun TimeScreen(
    onOpenWatchList: () -> Unit = {},
    timeViewModel: TimeViewModel = hiltViewModel(),
) {
    val state by timeViewModel.state.collectAsState()

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TimeTitleRow(onOpenWatchList)
                // The stored values are on screen; the watch's come in the background.
                if (state.updating) {
                    androidx.compose.material3.Text(
                        text = stringResource(id = R.string.updating),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }

                LocalTimeView(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp)
                )

                TimerView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                )

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

/** The title row of the Time tab: the watch list button top left ("Watches"), the title in the middle. */
@Composable
internal fun TimeTitleRow(onOpenWatchList: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        ScreenTitle(stringResource(id = R.string.time), Modifier.align(Alignment.Center))
        IconButton(onClick = onOpenWatchList, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.Filled.Watch,
                contentDescription = stringResource(id = R.string.watches),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTimeScreen() {
    TimeScreen()
}
