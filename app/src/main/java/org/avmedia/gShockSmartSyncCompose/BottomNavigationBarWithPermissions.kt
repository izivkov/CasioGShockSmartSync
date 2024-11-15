package org.avmedia.gShockSmartSyncCompose

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_CALENDAR
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.avmedia.gShockSmartSyncCompose.ui.actions.ActionsScreen
import org.avmedia.gShockSmartSyncCompose.ui.alarms.AlarmsScreen
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import org.avmedia.gShockSmartSyncCompose.ui.events.EventsScreen
import org.avmedia.gShockSmartSyncCompose.ui.settings.SettingsScreen
import org.avmedia.gShockSmartSyncCompose.ui.time.TimeScreen

@Composable
fun BottomNavigationBarWithPermissions() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.padding(0.dp)
            ) {
                BottomNavigationItem().bottomNavigationItems().forEachIndexed { _, navigationItem ->
                    NavigationBarItem(
                        selected = navigationItem.route == currentDestination?.route,
                        label = {
                            Text(navigationItem.label)
                        },
                        icon = {
                            Icon(
                                navigationItem.icon,
                                contentDescription = navigationItem.label
                            )
                        },
                        onClick = {
                            navController.navigate(navigationItem.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        alwaysShowLabel = false,
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screens.Time.route,
            modifier = Modifier.padding(paddingValues = paddingValues)
        ) {
            composable(Screens.Time.route) {
                TimeScreen(
                    navController
                )
            }
            composable(Screens.Alarms.route) {
                AlarmsScreen()
            }
            composable(Screens.Events.route) {
                PermissionRequiredScreen(
                    requiredPermissions = listOf(READ_CALENDAR),
                    onPermissionGranted = { EventsScreen() },
                    onPermissionDenied = {
                        LaunchedEffect(Unit) { // make sure it is only called once
                            AppSnackbar(
                                "Calendar permission denied.  Cannot access Events.",
                            )
                        }
                        navController.navigate(Screens.Time.route)
                    }
                )
            }
            composable(Screens.Actions.route) {

                val permissions = mutableListOf(CAMERA, CALL_PHONE).also {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) it += WRITE_EXTERNAL_STORAGE
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) it += ACCESS_FINE_LOCATION
                }

                PermissionRequiredScreen(
                    requiredPermissions = permissions,
                    onPermissionGranted = { ActionsScreen(navController) },
                    onPermissionDenied = {
                        LaunchedEffect(Unit) { // make sure it is only called once
                            AppSnackbar(
                                "Required permissions denied. Cannot access Actions.",
                            )
                        }
                        navController.navigate(Screens.Time.route)
                    }
                )
            }
            composable(Screens.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    requiredPermissions: List<String>,
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable () -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionChecked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            permissionGranted = permissions.values.all { it }
            permissionChecked = true
        }
    )

    LaunchedEffect(Unit) {
        launcher.launch(requiredPermissions.toTypedArray())
    }

    if (permissionChecked) {
        if (permissionGranted) {
            onPermissionGranted() // Safe to call @Composable here
        } else {
            onPermissionDenied()  // Safe to call @Composable here
        }
    }
}
