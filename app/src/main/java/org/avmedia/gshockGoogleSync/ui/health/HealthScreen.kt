package org.avmedia.gshockGoogleSync.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
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
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val healthConnectManager = remember { HealthConnectManager(context) }
    var hasPermissions by remember { mutableStateOf(false) }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            viewModel.permissionsLauncher
        ) {
            // TODO
        }

    LaunchedEffect(Unit) {
        if (viewModel.permissionsGranted) {
            // TODO
        } else {
            permissionsLauncher.launch(healthConnectManager.PERMISSIONS)
        }
    }

    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, healthLayout, buttonsRow) = createRefs()

                ScreenTitle(
                    viewModel.translateApi.stringResource(
                        context = LocalContext.current,
                        id = R.string.health
                    ), Modifier
                        .constrainAs(title) {
                            top.linkTo(parent.top)  // Link top of content to parent top
                            bottom.linkTo(healthLayout.top)  // Link bottom of content to top of buttonsRow
                        })
                Column(
                    modifier = Modifier
                        .constrainAs(healthLayout) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(buttonsRow.top)
                            height = Dimension.fillToConstraints
                        }
                        .verticalScroll(rememberScrollState())  // Make content scrollable
                        .padding(0.dp)
                        .fillMaxWidth()
                        .fillMaxSize()
                ) {
                    ExerciseCard()
                    HeartRateCard()
                    SleepCard()
                }

                BottomRow(modifier = Modifier.constrainAs(buttonsRow) {
                    top.linkTo(healthLayout.bottom)  // Link top of buttonsRow to bottom of content
                    bottom.linkTo(parent.bottom)  // Keep buttons at the bottom
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
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
                    text =
                        healthViewModel.translateApi.stringResource(
                            context = LocalContext.current,
                            id = R.string.send_to_health_app
                        ),
                    onClick = { println("Sent to fit app...") }),
            )
            ButtonsRow(buttons = buttons)
        }
    }
}

@Composable
private fun PermissionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Health Connect permissions required",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {}) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
private fun ExerciseCard() {
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null)
                Text(
                    text = "Exercise",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Steps today: 0",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun HeartRateCard() {
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null)
                Text(
                    text = "Heart Rate",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                HeartRateMetric("Min", "-- bpm")
                HeartRateMetric("Avg", "-- bpm")
                HeartRateMetric("Max", "-- bpm")
            }
        }
    }
}

@Composable
private fun HeartRateMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SleepCard() {
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Nightlight, contentDescription = null)
                Text(
                    text = "Sleep",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last night: No data",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}