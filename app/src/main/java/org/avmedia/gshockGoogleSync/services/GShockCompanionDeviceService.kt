package org.avmedia.gshockGoogleSync.services

import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.datatransport.Event
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class GShockCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        Timber.i("Device appeared (Legacy API 31-32): $address")
        ProgressEvents.onNext("DeviceAppeared", sanitizeAddress(address))
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        Timber.i("Device disappeared (Legacy API 31-32): $address")
        ProgressEvents.onNext("DeviceDisappeared", sanitizeAddress(address))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: android.companion.AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        Timber.i("Device appeared (API 33+): $address")
        ProgressEvents.onNext("DeviceAppeared", sanitizeAddress(address))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: android.companion.AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        Timber.i("Device disappeared (API 33+): $address, associationId: ${associationInfo.id}")
        ProgressEvents.onNext("DeviceDisappeared", sanitizeAddress(address))
    }

    private fun sanitizeAddress(address: String): String {
        return address.uppercase().replace("-", ":")
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        super.onDevicePresenceEvent(event)

        Timber.i("onDevicePresenceEvent: $event")
    }
}
