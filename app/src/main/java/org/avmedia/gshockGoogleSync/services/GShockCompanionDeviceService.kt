package org.avmedia.gshockGoogleSync.services

import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.MainActivity
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import timber.log.Timber
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class GShockCompanionDeviceService : CompanionDeviceService() {

    @Inject
    lateinit var repository: GShockRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onDeviceAppeared(address: String) {
        Timber.i("Device appeared: $address")
        // Trigger connection attempt
        serviceScope.launch {
            if (!repository.isConnected()) {
                repository.waitForConnection(address)
            }
        }
    }

    override fun onDeviceDisappeared(address: String) {
        Timber.i("Device disappeared: $address")
    }
}
