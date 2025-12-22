package org.avmedia.gshockGoogleSync.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Inject

@AndroidEntryPoint
class GShockScanService : Service() {

    @Inject
    lateinit var repository: GShockRepository
    override fun onBind(intent: Intent?): IBinder? {
        println("Not yet implemented")
        return null
    }

    // This is called when you call context.startService(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startLoop()

        return START_STICKY // Keep service running
    }

    private fun startLoop() {
        val scope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Logic: Scan only if NOT connected
                if (!repository.isConnected()) {
                    repository.scan(
                        context = this@GShockScanService,
                        filter = { deviceInfo ->
                            val savedAddresses = repository.getAssociations(this@GShockScanService)
                            savedAddresses.contains(deviceInfo.address)
                        },
                        onDeviceFound = { deviceInfo ->
                            ProgressEvents.onNext("DeviceAppeared", deviceInfo.address)
                        }
                    )
                }
                delay(3000) // Don't spam scans, wait 3s between checks
            }
        }
    }
}
