package org.avmedia.gshockGoogleSync.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import java.util.Timer
import kotlin.concurrent.schedule

@Composable
fun CheckPermissions(onPermissionsGranted: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    fun getRequiredPermissions(): Array<String> {
        return mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addAll(
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val initialPermissions = getRequiredPermissions()

    // State to track if permissions are granted
    var permissionsGranted by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    // Launcher for requesting permissions
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            permissionsGranted = permissions.values.all { it }
            showRationaleDialog = permissions.values.any {
                !it && activity.shouldShowRequestPermissionRationale(initialPermissions[0])
            }
            permanentlyDenied = !permissionsGranted && !showRationaleDialog
        }
    )

    // Check permissions before launching the request
    LaunchedEffect(Unit) {
        val arePermissionsGranted = initialPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (arePermissionsGranted) {
            permissionsGranted = true
        } else {
            launcher.launch(initialPermissions)
        }
    }

    // Trigger callbacks based on permission status
    if (permissionsGranted) {
        onPermissionsGranted()
    }

    // Show rationale dialog if needed
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { /* Do nothing */ },
            title = { Text(text = "Permissions Required") },
            text = { Text("This app needs location and Bluetooth permissions to function properly.") },
            confirmButton = {
                AppButton(
                    "Retry",
                    onClick = {
                        launcher.launch(initialPermissions)
                    })
            },
            dismissButton = {
                AppButton("Exit", onClick = {
                    activity.finish()  // Exit if user doesn't want to grant permissions
                })
            }
        )
    }

    if (permanentlyDenied) {

        AppSnackbar(
            stringResource(
                R.string.permissions_are_permanently_denied_please_enable_them_in_the_app_settings
            )
        )
        Timer("SettingUp", false).schedule(6000) { activity.finish() }
    }
}

