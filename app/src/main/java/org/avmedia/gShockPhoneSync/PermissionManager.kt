/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-11, 12:40 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-11, 12:40 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.util.*

data class PermissionManager(val context: Context) {

    private var ENABLE_BLUETOOTH_REQUEST_CODE = 3
    private var PERMISSION_ALL = 1
    private var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS += Manifest.permission.BLUETOOTH_SCAN
            PERMISSIONS += Manifest.permission.BLUETOOTH_CONNECT
        }
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun setupPermissions() {
        if (!hasPermissions(context, PERMISSIONS)) {
            ActivityCompat.requestPermissions(context as Activity, PERMISSIONS, PERMISSION_ALL)
        } else {
            ProgressEvents.onNext("FineLocationPermissionGranted")
        }
    }

    fun hasAllPermissions(): Boolean {
        return hasPermissions(context, PERMISSIONS)
    }

    @SuppressLint("MissingPermission")
    fun promptEnableBluetooth() {
        if (!(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                (context as Activity).startActivityForResult(
                    enableBtIntent,
                    ENABLE_BLUETOOTH_REQUEST_CODE
                )
                Timber.i("Return from startActivityForResult")
            } catch (e: SecurityException) {
                Utils.snackBar(context, "Please turn on BlueTooth and restart the app...")
                (context as Activity).finish()
            }
        }
    }

    fun onRequestPermissionsResult(
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        permissions.forEachIndexed { index, permission ->
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION -> {
                    ProgressEvents.onNext(
                        if (grantResults[index] == 0) "FineLocationPermissionGranted" else "FineLocationPermissionNotGranted"
                    )
                }
                // ignore the rest. They are handled in their fragments
            }
        }
    }
}
