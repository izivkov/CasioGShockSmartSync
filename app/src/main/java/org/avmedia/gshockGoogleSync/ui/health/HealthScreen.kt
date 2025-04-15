package org.avmedia.gshockGoogleSync.ui.health

import AppText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun HealthScreen(
    onDenyPermissionsOrNotInstalled: () -> Unit,
    healthViewModel: HealthViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val steps by healthViewModel.steps.collectAsState()
    val minHeartRate by healthViewModel.minHeartRate.collectAsState()
    val maxHeartRate by healthViewModel.maxHeartRate.collectAsState()
    val avgHeartRate by healthViewModel.avgHeartRate.collectAsState()
    val sleepDuration by healthViewModel.sleepDuration.collectAsState()

    val healthConnectManager = remember { HealthConnectManager(context) }
    var showPermissionsCard by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { _ ->
            scope.launch {
                val hasAllPermissions = healthConnectManager.hasPermissions()
                showPermissionsCard = !hasAllPermissions
                if (hasAllPermissions) {
                    healthViewModel.startDataCollection(healthConnectManager)
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        val hasPermissions = healthConnectManager.hasPermissions()
        showPermissionsCard = !hasPermissions
        if (hasPermissions) {
            healthViewModel.startDataCollection(healthConnectManager)
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
                    healthViewModel.translateApi.stringResource(context, R.string.health),
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
                        PermissionsCard(
                            onGrantClick = { permissionLauncher.launch(healthConnectManager.permissions) },
                            onDenyPermissions = onDenyPermissionsOrNotInstalled
                        )
                    } else {
                        // Your health cards
                        ExerciseCard(steps)
                        HeartRateCard(
                            minRate = minHeartRate,
                            avgRate = avgHeartRate,
                            maxRate = maxHeartRate
                        )
                        SleepCard(sleepDuration)
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

@Composable
fun BottomRow(
    modifier: Modifier,
    healthViewModel: HealthViewModel = hiltViewModel()
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,  // Center vertically
            horizontalArrangement = Arrangement.SpaceEvenly,  // Arrange horizontally, starting from the left
        ) {
            val buttons = arrayListOf(
                ButtonData(
                    text = healthViewModel.translateApi.stringResource(
                        context = LocalContext.current,
                        id = R.string.send_to_health_app
                    ),
                    onClick = { healthViewModel.sendToHealthApp() })
            )

            ButtonsRow(buttons = buttons)
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
private fun SleepCard(duration: Int) {
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
                text = String.format("$duration")
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    onGrantClick: () -> Unit,
    onDenyPermissions: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AppText(
                text = "Permission request",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AppText(
                text = "To track your health data, G-Shock Smart Sync needs access to:",
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Permissions list
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                AppText("• Steps data")
                AppText("• Heart rate data")
                AppText("• Sleep data")
            }

            // Buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDenyPermissions
                ) {
                    AppText("Deny")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onGrantClick
                ) {
                    AppText("Allow")
                }
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
