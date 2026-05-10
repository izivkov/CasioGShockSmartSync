package org.avmedia.gshockGoogleSync.receivers

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.avmedia.gshockGoogleSync.GShockApplication
import timber.log.Timber

/**
 * Listens for Bluetooth becoming available (STATE_ON) and re-triggers the
 * device discovery/connection pipeline. This handles the boot-time race condition
 * where BOOT_COMPLETED fires before the Bluetooth adapter is fully ready.
 */
class BluetoothStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        if (state == BluetoothAdapter.STATE_ON) {
            Timber.i("BluetoothStateReceiver: BT turned ON — re-initializing device scan")
            (context.applicationContext as? GShockApplication)?.init()
        }
    }
}
