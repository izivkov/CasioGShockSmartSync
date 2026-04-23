package org.avmedia.gshockGoogleSync.services

import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.datatransport.Event
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import android.companion.CompanionDeviceManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import org.avmedia.gshockGoogleSync.R

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class GShockCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        Timber.i("Device appeared (Legacy API 31-32): $address")
        startForegroundServicePromotion()
        ProgressEvents.onNext("DeviceAppeared", sanitizeAddress(address))
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        Timber.i("Device disappeared (Legacy API 31-32): $address")
        ProgressEvents.onNext("DeviceDisappeared", sanitizeAddress(address))
        
        // Re-arm the observation so next appearance fires again
        val cdm = getSystemService(CompanionDeviceManager::class.java)
        try {
            @Suppress("DEPRECATION")
            cdm.startObservingDevicePresence(address)
            Timber.i("Re-armed device presence observation for $address")
        } catch (e: Exception) {
            Timber.e(e, "Failed to re-arm device presence observation")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: android.companion.AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        Timber.i("Device appeared (API 33+): $address")
        startForegroundServicePromotion()
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

        val companionDeviceManager = getSystemService(CompanionDeviceManager::class.java)
        val associationInfo = companionDeviceManager.myAssociations.find { it.id == event.associationId }
        val address = associationInfo?.deviceMacAddress?.toString() ?: return

        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED, DevicePresenceEvent.EVENT_BT_CONNECTED -> {
                Timber.i("Device appeared (API 36+): $address")
                startForegroundServicePromotion()
                ProgressEvents.onNext("DeviceAppeared", sanitizeAddress(address))
            }
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED, DevicePresenceEvent.EVENT_BT_DISCONNECTED -> {
                Timber.i("Device disappeared (API 36+): $address")
                ProgressEvents.onNext("DeviceDisappeared", sanitizeAddress(address))
            }
        }
    }

    private fun startForegroundServicePromotion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val channelId = "gshock_companion_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Device Connection",
                    NotificationManager.IMPORTANCE_LOW
                )
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Watch Connecting")
                .setContentText("Keeping connection alive...")
                .setSmallIcon(R.drawable.ic_watch_later_black_24dp)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1983, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(1983, notification)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start foreground service")
            }
        }
    }
}
