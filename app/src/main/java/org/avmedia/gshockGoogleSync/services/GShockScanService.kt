package org.avmedia.gshockGoogleSync.services

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("GShockScanService: stopping because BLUETOOTH_SCAN is not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        GShockScanner.startScan(
            context       = applicationContext,
            isBluetoothOn = { bluetoothAdapter?.isEnabled == true },
            filter        = { info ->
                val knownAddresses =
                    (repository.getAssociations(this) + LocalDataStorage.getDeviceAddresses(this))
                        .map { it.uppercase() }
                        .toSet()
                knownAddresses.contains(info.address.uppercase())
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
