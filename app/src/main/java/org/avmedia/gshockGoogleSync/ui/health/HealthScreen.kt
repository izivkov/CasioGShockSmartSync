package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import org.avmedia.gshockapi.WatchInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun HealthScreen() {
    var showProfileDialog by remember { mutableStateOf(false) }
    val healthViewModel: HealthViewModel = hiltViewModel()
    val state by healthViewModel.state.collectAsState()
    // DW-H5600: until its LifeLog arrives, show only the loading message
    val waitingForLifeLog = WatchInfo.model == WatchInfo.WatchModel.DW_H5600 &&
            state.stepCounterData.h5600 == null && state.lifeLogLoading

    // Every open shows the stored day at once and starts one read by itself (none within a minute of the last).
    LaunchedEffect(Unit) {
        healthViewModel.onAction(HealthAction.ScreenOpened)
    }

    // The screen polls while it is on screen (the ABL-100's steps change all day) and stops when it is left.
    DisposableEffect(Unit) {
        healthViewModel.onScreenShown()
        onDispose { healthViewModel.onScreenHidden() }
    }

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(
                    stringResource(id = R.string.health),
                    Modifier
                )

                if (waitingForLifeLog) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = R.string.loading_lifelog_short),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Steps card — shows Today, Hourly, and Daily panels all at once
                        StepCounterView(
                            modifier = Modifier.fillMaxWidth(),
                            healthViewModel = healthViewModel
                        )

                        // DW-H5600 LifeLog details (nothing is shown for other watches)
                        DwH5600LifeLogCard(
                            modifier = Modifier.fillMaxWidth(),
                            healthViewModel = healthViewModel
                        )

                        // Week and month charts, calendar and export of what the watch has delivered so far
                        if (WatchInfo.model == WatchInfo.WatchModel.DW_H5600) {
                            org.avmedia.gshockGoogleSync.ui.health.history.HistorySection(Modifier.fillMaxWidth())
                            org.avmedia.gshockGoogleSync.ui.health.history.WorkoutsSection(Modifier.fillMaxWidth())
                        }

                        // Profile button: the ABL-100 profile does not apply to the DW-H5600
                        if (WatchInfo.model != WatchInfo.WatchModel.DW_H5600) {
                            AppButton(
                                text = stringResource(id = R.string.profile),
                                onClick = { showProfileDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (state.updating) {
                    Text(
                        text = stringResource(id = R.string.updating),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    // Exercise profile dialog — shown when user taps the Profile button
    if (showProfileDialog && WatchInfo.model != WatchInfo.WatchModel.DW_H5600) {
        ProfileDialog(
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHealthScreen() {
    HealthScreen()
}