package org.avmedia.gshockGoogleSync.ui.time

import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeScreen(timeViewModel: TimeViewModel = hiltViewModel()) {
    val timeFormatViewModel: TimeFormatViewModel = hiltViewModel()
    val isRefreshing by timeViewModel.isRefreshing.collectAsState()
    val timeState by timeViewModel.state.collectAsState()

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(
                    stringResource(id = R.string.time),
                    Modifier
                )

                // Swipe down: re-read battery, sensor temperature, home time, timer and time format.
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        timeViewModel.onAction(TimeAction.PullRefresh)
                        timeFormatViewModel.refresh()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        LocalTimeView(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.dp)
                        )

                        // Fine Adjustment (moved here from Settings)
                        FineAdjustmentCard(modifier = Modifier.fillMaxWidth())

                        // Minsk and Los Angeles clocks (phone-side)
                        WorldClocksView(modifier = Modifier.fillMaxWidth())

                        TimerView(modifier = Modifier.fillMaxWidth())

                        // 12h / 24h on the watch (moved here from Settings)
                        TimeFormatRow(modifier = Modifier.fillMaxWidth(), vm = timeFormatViewModel)

                        Spacer(modifier = Modifier.height(12.dp))

                        WatchInfoView(modifier = Modifier.fillMaxWidth())
                    }
                }

                // Watch name as a small line pinned at the bottom (was a large card)
                AppText(
                    text = timeState.watchName,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
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
