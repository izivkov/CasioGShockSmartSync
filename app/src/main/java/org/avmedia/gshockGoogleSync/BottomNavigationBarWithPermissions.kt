package org.avmedia.gshockGoogleSync

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.EntryPointAccessors
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.di.ApplicationContextEntryPoint
import org.avmedia.gshockGoogleSync.services.InactivityHandler
import org.avmedia.gshockGoogleSync.ui.actions.ActionsScreen
import org.avmedia.gshockGoogleSync.ui.alarms.AlarmsScreen
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.events.EventsScreen
import org.avmedia.gshockGoogleSync.ui.settings.SettingsScreen
import org.avmedia.gshockGoogleSync.ui.time.TimeScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun BottomNavigationBarWithPermissions(
    repository: GShockRepository,
    translateApi: TranslateRepository,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val localContext = LocalContext.current.applicationContext
    val appContext = remember {
        EntryPointAccessors.fromApplication(
            localContext,
            ApplicationContextEntryPoint::class.java
        ).getApplicationContext()
    }

    val inactivityHandler = remember {
        InactivityHandler(timeout = (3 * 60).seconds) {
            repository.disconnect()
            AppSnackbar(translateApi.getString(appContext, R.string.disconnected_due_to_inactivity))
        }
    }

    DisposableEffect(Unit) {
        inactivityHandler.startMonitoring()
        onDispose {
            inactivityHandler.stopMonitoring()
        }
    }

    fun Modifier.detectInactivity(handler: InactivityHandler): Modifier {
        return this.pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitPointerEvent()
                    handler.registerInteraction()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .detectInactivity(inactivityHandler),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.padding(0.dp)
            ) {
                BottomNavigationItem(translateApi = translateApi).bottomNavigationItems()
                    .forEachIndexed { _, navigationItem ->
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
                TimeScreen()
            }
            composable(Screens.Alarms.route) {
                AlarmsScreen()
            }
            composable(Screens.Events.route) {
                NavigateWithPermissions(
                    listOf(READ_CALENDAR),
                    navController,
                    destinationScreen = { EventsScreen() },
                    translateApi.stringResource(
                        LocalContext.current,
                        R.string.calendar_permission_denied_cannot_access_events,
                    ),
                    translateApi
                )
            }
            composable(Screens.Actions.route) {

                val permissions = mutableListOf(CAMERA, CALL_PHONE).also {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                        it += WRITE_EXTERNAL_STORAGE
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it += ACCESS_FINE_LOCATION
                    }
                }

                NavigateWithPermissions(
                    permissions,
                    navController,
                    destinationScreen = { ActionsScreen() },
                    translateApi.stringResource(
                        LocalContext.current,
                        R.string.required_permissions_denied_cannot_access_actions
                    ),
                    translateApi
                )
            }
            composable(Screens.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun NavigateWithPermissions(
    requiredPermissions: List<String>,
    navController: NavController,
    destinationScreen: @Composable () -> Unit,
    errorMessage: String = stringResource(R.string.required_permissions_denied_cannot_access_screen),
    translateApi: TranslateRepository,
) {
    var hasNavigated by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (requiredPermissions.isEmpty()) {
        return
    }

    PermissionRequiredScreen(
        requiredPermissions = requiredPermissions,
        onPermissionGranted = { destinationScreen() },
        onPermissionDenied = {
            if (!hasNavigated) {
                hasNavigated = true
                LaunchedEffect(Unit) { // make sure it is only called once
                    val additionalInfo =
                        translateApi.getString(
                            context,
                            R.string.clear_app_storage_from_android_setting_and_restart_the_app_to_add_permissions
                        )
                    AppSnackbar(errorMessage + additionalInfo)
                }
                navController.navigate(Screens.Time.route) {
                    popUpTo(Screens.Time.route) {
                        inclusive = true
                    } // Avoid back stack issues
                }
            }
        }
    )
}

@Composable
fun PermissionRequiredScreen(
    requiredPermissions: List<String>,
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable () -> Unit
) {
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
