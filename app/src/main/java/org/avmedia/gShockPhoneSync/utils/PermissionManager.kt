/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-11, 12:40 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-11, 12:40 p.m.
 */

@file:Suppress("PrivatePropertyName")

package org.avmedia.gShockPhoneSync.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import org.avmedia.gshockapi.ProgressEvents

data class PermissionManager(val context: Context) {

    private var PERMISSION_ALL = 1
    private var PERMISSIONS = arrayOf<String>()

    init {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            PERMISSIONS += Manifest.permission.ACCESS_FINE_LOCATION
        }
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
