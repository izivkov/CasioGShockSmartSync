package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockGoogleSync.pairing.H5600ConnectPolicy
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

    // Refresh every time the screen opens, so a LifeLog read that finished while the screen
    // was closed (or before the watch connected) reaches the cards.
    LaunchedEffect(Unit) {
        // Opening the Health screen is the user starting a sync: allow the DW-H5600 to connect.
        if (WatchInfo.model == WatchInfo.WatchModel.DW_H5600) H5600ConnectPolicy.arm()
        healthViewModel.onAction(HealthAction.RefreshState)
    }

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(
                    stringResource(id = R.string.health),
                    Modifier
                )

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
