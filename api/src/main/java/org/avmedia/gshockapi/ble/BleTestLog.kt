package org.avmedia.gshockapi.ble

import android.bluetooth.le.ScanResult
import timber.log.Timber

/**
 * Test logging of CASIO watch advertisements, before any filtering. Every line starts with "BLE_TEST",
 * so the tests can be followed with: adb logcat | grep BLE_TEST
 */
object BleTestLog {
    fun advertisement(result: ScanResult) {
        val name = result.scanRecord?.deviceName ?: result.device?.name ?: return
        if (!name.contains("CASIO")) return
        val address = result.device?.address ?: return
        val bytes = result.scanRecord?.bytes?.joinToString(" ") { "%02X".format(it) } ?: "-"
        Timber.i(
            "BLE_TEST adv t=${System.currentTimeMillis()} addr=$address name=$name rssi=${result.rssi} " +
                    "connectable=${result.isConnectable} legacy=${result.isLegacy} phy=${result.primaryPhy} " +
                    "bytes=$bytes",
        )
    }
}