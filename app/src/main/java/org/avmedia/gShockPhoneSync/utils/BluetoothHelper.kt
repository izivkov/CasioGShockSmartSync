package org.avmedia.gShockPhoneSync.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import org.avmedia.gShockPhoneSync.ui.common.AppSnackbar

class BluetoothHelper(
    private val context: Context,
    private val activity: Activity,
    private val requestBluetooth: ActivityResultLauncher<Intent>,
    private val onBluetoothEnabled: () -> Unit,
    private val onBluetoothNotEnabled: () -> Unit
) {
    fun turnOnBLE() {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            AppSnackbar("Sorry, your device does not support Bluetooth. Exiting...")
            activity.finish()
            return
        }

        if (bluetoothAdapter.isEnabled.not()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            try {
                requestBluetooth.launch(enableBtIntent)
            } catch (e: SecurityException) {
                AppSnackbar("You have no permissions to turn on Bluetooth. Please turn it on manually.")
            }
        }
    }
}