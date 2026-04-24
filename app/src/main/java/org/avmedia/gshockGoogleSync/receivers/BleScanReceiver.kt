package org.avmedia.gshockGoogleSync.receivers

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockGoogleSync.utils.DeviceEventGate
import timber.log.Timber

class BleScanReceiver : BroadcastReceiver() {

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

        scanResults?.forEach { result ->
            result.device?.address?.let { address ->
                val upperAddress = address.uppercase()
                if (!DeviceEventGate.recordBleEvent(upperAddress, "DeviceAppeared")) {
                    Timber.d("BleScanReceiver: suppressing DeviceAppeared for $upperAddress")
                    return@let
                }
                Timber.i("BleScanReceiver found device: $upperAddress")
                ProgressEvents.onNext("DeviceAppeared", upperAddress)
            }
        }
    }
}
