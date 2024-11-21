package org.avmedia.gshockGoogleSync.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import org.avmedia.gshockapi.GShockAPI

class BluetoothManager(
    private val context: Context,
    private val activity: Activity,
    private val requestBluetooth: ActivityResultLauncher<Intent>
) {
    fun checkBluetoothEnabled(api: GShockAPI, onBluetoothEnabled: () -> Unit) {
        if (!api.isBluetoothEnabled(context)) {
            turnOnBLE()
        } else {
            onBluetoothEnabled()
        }
    }

    private fun turnOnBLE() {
        requestBluetooth.launch(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
    }
}
