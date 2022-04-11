/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-11, 12:40 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-11, 12:40 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

data class PermissionManager(val context: Context) {

    private var ENABLE_BLUETOOTH_REQUEST_CODE = 3
    private var PERMISSION_ALL = 1
    private var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALENDAR,
    )
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    public fun setupPermissions(){
        if (!hasPermissions(context, PERMISSIONS)) {
            ActivityCompat.requestPermissions(context as Activity, PERMISSIONS, PERMISSION_ALL);
        }
    }

    public fun hasAllPermissions () : Boolean {
        return hasPermissions(context, PERMISSIONS)
    }

    fun promptEnableBluetooth() {
        if (!(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            (context as Activity).startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }
}
