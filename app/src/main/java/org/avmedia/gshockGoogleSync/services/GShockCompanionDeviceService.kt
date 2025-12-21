package org.avmedia.gshockGoogleSync.services

import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class GShockCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        Timber.i("Device appeared: $address")
        ProgressEvents.onNext("DeviceAppeared", address)
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        Timber.i("Device disappeared: $address")
        ProgressEvents.onNext("DeviceDisappeared", address)
    }
}
