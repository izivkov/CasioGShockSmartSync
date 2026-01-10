package org.avmedia.gshockGoogleSync.ui.others

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission

object CompanionDeviceHelper {
    private const val EXTRA_ASSOC = "android.companion.extra.ASSOCIATION_INFO"
    private const val KEY_ADDRESS = "address"
    private const val KEY_ID = "id"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val UNKNOWN_DEVICE = "Unknown"

    /**
     * Extracts a BluetoothDevice from the CDM result intent.
     * Checks multiple keys and fallback types (ScanResult/AssociationInfo).
     */
    fun extractDevice(data: Intent?): BluetoothDevice? {
        if (data == null) return null

        // 1. Standard BluetoothDevice extraction
        var device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }

        // 2. Fallback: Check for ScanResult wrapping
        if (device == null) {
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
            val info = data.getParcelableExtra(EXTRA_ASSOC, AssociationInfo::class.java)
            info?.deviceMacAddress?.let { mac ->
                val adapter = BluetoothAdapter.getDefaultAdapter()
                device = adapter.getRemoteDevice(mac.toString().uppercase())
            }
        }

        return device
    }

    /**
     * Queries the OS for a list of all currently associated devices.
     * Returns a list of dicts containing device metadata.
     */
    fun getAssociationsFallback(context: Context): List<Map<String, String>> {
        val cdm = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        val associationList: MutableList<Map<String, String>> = mutableListOf()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.myAssociations.forEach { info ->
                info.deviceMacAddress?.toString()?.let { addr ->
                    val deviceData: Map<String, String> = mapOf(
                        KEY_ADDRESS to addr.uppercase(),
                        KEY_ID to info.id.toString(),
                        KEY_DISPLAY_NAME to (info.displayName?.toString() ?: UNKNOWN_DEVICE)
                    )
                    associationList.add(deviceData)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            cdm.associations.forEach { addr ->
                val deviceData: Map<String, String> = mapOf(
                    KEY_ADDRESS to addr.uppercase()
                )
                associationList.add(deviceData)
            }
        }
        return associationList
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
