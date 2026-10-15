package org.avmedia.gshockGoogleSync.pairing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import timber.log.Timber

object CompanionDeviceHelper {
    private const val EXTRA_ASSOC = "android.companion.extra.ASSOCIATION_INFO"
    private const val KEY_ADDRESS = "address"
    private const val UNKNOWN_DEVICE = "Unknown"

    /**
     * Extracts a BluetoothDevice from the CDM result intent.
     * Checks multiple keys and fallback types (ScanResult/AssociationInfo).
     */
    fun extractDevice(data: Intent?): BluetoothDevice? {
        if (data == null) {
            Timber.i("Intent empty, giving up device extraction.")
            return null
        }

        // 1. Standard BluetoothDevice extraction
        var device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }

        // 2. Fallback: Check for ScanResult wrapping
        if (device == null) {
            Timber.i("Intent empty, checking ScanResult wrapping...")
            val scanResult: ScanResult? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            }
            device = scanResult?.device
        }

        // 3. Fallback: Check for AssociationInfo (Android 13+)
        if (device == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Timber.i("Intent empty, checking AssociationInfo...")
            val info = data.getParcelableExtra(EXTRA_ASSOC, AssociationInfo::class.java)
            info?.deviceMacAddress?.let { mac ->
                val adapter = BluetoothAdapter.getDefaultAdapter()
                device = adapter.getRemoteDevice(mac.toString().uppercase())
            }
        }

        Timber.i("Extracted device: ${device?.address ?: "null"}")
        return device
    }

    /**
     * Creates a standardized log map for a device.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun createDeviceLog(device: BluetoothDevice): Map<String, String> {
        val log: Map<String, String> = mapOf(
            "mac_address" to device.address,
            "device_name" to (device.name ?: UNKNOWN_DEVICE),
            "bond_state" to device.bondState.toString(),
            "timestamp" to System.currentTimeMillis().toString()
        )
        return log
    }
}
