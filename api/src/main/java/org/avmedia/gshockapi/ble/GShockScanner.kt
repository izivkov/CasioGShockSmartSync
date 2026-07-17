package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.ble.GShockScanner.BLUETOOTH_RETRY_DELAY_MS
import org.avmedia.gshockapi.ble.GShockScanner.SCAN_DURATION_MS
import org.avmedia.gshockapi.ble.GShockScanner.SCAN_INTERVAL_MS
import timber.log.Timber

object GShockScanner {

    private var loopJob: Job? = null
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Start a repeating scan loop:
     *   - scan for [SCAN_DURATION_MS] ms
     *   - pause for [SCAN_INTERVAL_MS] ms, then repeat
     *   - if BT is off, wait [BLUETOOTH_RETRY_DELAY_MS] ms and retry
     *   - each address fires [onDeviceFound] at most once per scan window
     *
     * Safe to call multiple times — ignores the call if already running.
     */
    @SuppressLint("MissingPermission")
    fun startScan(
        context: Context,
        isBluetoothOn: () -> Boolean,
        filter: (DeviceInfo) -> Boolean,
        onDeviceFound: (DeviceInfo) -> Unit
    ) {
        if (loopJob?.isActive == true) {
            Timber.d("GShockScanner: already running, ignoring startScan()")
            return
        }

        val bluetoothAdapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val bleScanner = bluetoothAdapter.bluetoothLeScanner

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setDeviceName(null)
                .build()
        )

        loopJob = loopScope.launch {
            Timber.i("GShockScanner: scan loop started")

            while (isActive) {
                if (!isBluetoothOn()) {
                    Timber.w("GShockScanner: Bluetooth not enabled, waiting...")
                    delay(BLUETOOTH_RETRY_DELAY_MS)
                    continue
                }

                var lastFoundAddress: String? = null

                val scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val address = result.device.address ?: return
                        val name = result.device.name ?: return

                        if (address == lastFoundAddress) return
                        lastFoundAddress = address

                        val info = DeviceInfo(name, address)
                        if (filter(info)) {
                            Timber.i("GShockScanner: matched $address ($name)")
                            onDeviceFound(info)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        Timber.e("GShockScanner: scan failed with error $errorCode")
                    }
                }

                Timber.d("GShockScanner: scan window opening")
                try {
                    bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
                } catch (e: SecurityException) {
                    Timber.e(e, "GShockScanner: missing BLE scan permission")
                }

                delay(SCAN_DURATION_MS)

                try {
                    bleScanner?.stopScan(scanCallback)
                } catch (e: SecurityException) {
                    Timber.e(e, "GShockScanner: missing BLE scan permission")
                }
                Timber.d("GShockScanner: scan window closed, pausing ${SCAN_INTERVAL_MS}ms")

                delay(SCAN_INTERVAL_MS)
            }

            Timber.i("GShockScanner: scan loop ended")
        }
    }

    @SuppressLint("MissingPermission")
    fun startFallbackScan(
        context: Context,
        addresses: List<String>,
        pendingIntent: android.app.PendingIntent
    ) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return

        // Stop any previously active scan (handles both current session and previous app runs)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scanner.stopScan(pendingIntent)
                Timber.i("GShockScanner: Stopped previous fallback scan")
            }
        } catch (e: Exception) {
            // This is expected if no scan was running
        }

        if (addresses.isEmpty()) {
            return
        }

        val filters = addresses.map { address ->
            ScanFilter.Builder()
                .setDeviceAddress(address.uppercase())
                .build()
        }

        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scanner.startScan(filters, settings, pendingIntent)
                Timber.i("GShockScanner: Started fallback PendingIntent scan for ${addresses.size} device(s): $addresses")
            }
        } catch (e: Exception) {
            Timber.e(e, "GShockScanner: Failed to start fallback scan")
        }
    }

    /** Stop all scanning immediately. Safe to call multiple times. */
    fun stopScan() {
        loopJob?.cancel()
        loopJob = null
        Timber.i("GShockScanner: stopped")
    }

    // ── timing constants — single source of truth ─────────────────────────────
    private const val SCAN_DURATION_MS = 5_000L
    private const val SCAN_INTERVAL_MS = 3_000L
    private const val BLUETOOTH_RETRY_DELAY_MS = 10_000L
}