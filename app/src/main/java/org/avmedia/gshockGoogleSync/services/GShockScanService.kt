package org.avmedia.gshockGoogleSync.services

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.GShockScanner
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GShockScanService : Service() {

    @Inject
    lateinit var repository: GShockRepository

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("GShockScanService: started")

        GShockScanner.startScan(
            context       = applicationContext,
            isBluetoothOn = { bluetoothAdapter?.isEnabled == true },
            filter        = { info ->
                repository.getAssociations(this).contains(info.address)
            },
            onDeviceFound = { info ->
                Timber.i("GShockScanService: onDeviceFound address=${info.address} name=${info.name}")
                ProgressEvents.onNext("DeviceAppeared", info.address)
            }
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.i("GShockScanService: destroyed")
        GShockScanner.stopScan()
        super.onDestroy()
    }
}