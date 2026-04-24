package org.avmedia.gshockGoogleSync.receivers

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class BleScanReceiver : BroadcastReceiver() {

    companion object {
        // Suppress repeated DeviceAppeared events for the same address within this window.
        // 5 seconds is long enough to cover multiple low-power scan cycles but short
        // enough to react to a genuine re-appearance after disconnection.
        private const val DEDUP_WINDOW_MS = 5_000L

        private val lastSeenTimes = mutableMapOf<String, Long>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (errorCode != -1) {
            Timber.e("BleScanReceiver error: $errorCode")
            return
        }

        val scanResults: List<ScanResult>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        }

        val now = System.currentTimeMillis()
        scanResults?.forEach { result ->
            result.device?.address?.let { address ->
                val upperAddress = address.uppercase()
                val lastSeen = lastSeenTimes[upperAddress] ?: 0L
                if (now - lastSeen < DEDUP_WINDOW_MS) {
                    Timber.d("BleScanReceiver: suppressing duplicate DeviceAppeared for $upperAddress")
                    return@let
                }
                lastSeenTimes[upperAddress] = now
                Timber.i("BleScanReceiver found device: $upperAddress")
                ProgressEvents.onNext("DeviceAppeared", upperAddress)
            }
        }
    }
}
