package org.avmedia.gshockGoogleSync.ui.health

import AppText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockGoogleSync.ui.settings.BottomRow
import androidx.health.connect.client.PermissionController

@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val healthConnectManager = remember { HealthConnectManager(context) }
    var showPermissionsCard by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.requestPermissionsActivityContract(),
        onResult = { granted ->
            if (granted.containsAll(healthConnectManager.permissions)) {
                showPermissionsCard = false
                viewModel.startDataCollection(healthConnectManager)
            }
            else {
                showPermissionsCard = true
            }
        }
    )

    LaunchedEffect(Unit) {
        val hasPermissions = healthConnectManager.hasPermissions()
        showPermissionsCard = !hasPermissions
        if (hasPermissions) {
            viewModel.startDataCollection(healthConnectManager)
        }
    }

    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (title, healthLayout, buttonsRow) = createRefs()

                ScreenTitle(
                    viewModel.translateApi.stringResource(context, R.string.health),
                    Modifier.constrainAs(title) {
                        top.linkTo(parent.top)
                        bottom.linkTo(healthLayout.top)
                    }
                )

                Column(
                    modifier = Modifier
                        .constrainAs(healthLayout) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(buttonsRow.top)
                            height = Dimension.fillToConstraints
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(0.dp)
                        .fillMaxWidth()
                ) {
                    if (showPermissionsCard) {
                        PermissionsCard {
                            permissionLauncher.launch(healthConnectManager.permissions)
                        }
                    } else {
                        // Your health cards
                        ExerciseCard(viewModel.steps)
                        HeartRateCard(
                            minRate = viewModel.minHeartRate,
                            avgRate = viewModel.avgHeartRate,
                            maxRate = viewModel.maxHeartRate
                        )
                        SleepCard(viewModel.sleepDuration)
                    }
                }

                BottomRow(
                    modifier = Modifier.constrainAs(buttonsRow) {
                        top.linkTo(healthLayout.bottom)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                )
            }
        }
    }
}

// Your existing composable functions remain the same
@Composable
private fun HeartRateMetric(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        AppText(
            text = label,
        )
        AppText(
            text = value,
        )
    }
}

@Composable
private fun HeartRateCard(minRate: Int, avgRate: Int, maxRate: Int) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MonitorHeart,
                    contentDescription = "Heart Rate",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    text = "Heart Rate",
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                HeartRateMetric("Min", if (minRate > 0) "$minRate bpm" else "-- bpm")
                HeartRateMetric("Avg", if (avgRate > 0) "$avgRate bpm" else "-- bpm")
                HeartRateMetric("Max", if (maxRate > 0) "$maxRate bpm" else "-- bpm")
            }
        }
    }
}

@Composable
private fun SleepCard(duration: String) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Nightlight,
                    contentDescription = "Sleep",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    text = "Sleep Duration",
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AppText(
                text = duration,
            )
        }
    }
}

@Composable
private fun PermissionsCard(onGrantClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppText(
                text = "Health Connect Permissions Required",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AppText(
                text = "Please grant permissions to access health data",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = onGrantClick,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                AppText("Grant Permissions")
            }
        }
    }
}

@Composable
private fun ExerciseCard(steps: Int) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = "Steps",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    text = "Daily Steps",
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AppText(
                text = "$steps steps",
            )
        }
    }
}
