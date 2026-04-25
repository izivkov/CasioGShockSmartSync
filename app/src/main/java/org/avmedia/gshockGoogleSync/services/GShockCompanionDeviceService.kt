package org.avmedia.gshockGoogleSync.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.utils.DeviceEventGate
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class GShockCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        handleDeviceEvent("DeviceAppeared", address, "Device appeared (Legacy API 31-32): $address")
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        handleDeviceEvent(
            "DeviceDisappeared",
            address,
            "Device disappeared (Legacy API 31-32): $address"
        )

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
        handleDeviceEvent("DeviceAppeared", address, "Device appeared (API 33+): $address")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: android.companion.AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        handleDeviceEvent(
            "DeviceDisappeared",
            address,
            "Device disappeared (API 33+): $address, associationId: ${associationInfo.id}"
        )
    }

    private fun sanitizeAddress(address: String): String {
        return address.uppercase().replace("-", ":")
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        super.onDevicePresenceEvent(event)

        val companionDeviceManager = getSystemService(CompanionDeviceManager::class.java)
        val associationInfo =
            companionDeviceManager.myAssociations.find { it.id == event.associationId }
        val address = associationInfo?.deviceMacAddress?.toString() ?: return

        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED, DevicePresenceEvent.EVENT_BT_CONNECTED -> {
                handleDeviceEvent("DeviceAppeared", address, "Device appeared (API 36+): $address")
            }

            DevicePresenceEvent.EVENT_BLE_DISAPPEARED, DevicePresenceEvent.EVENT_BT_DISCONNECTED -> {
                handleDeviceEvent(
                    "DeviceDisappeared",
                    address,
                    "Device disappeared (API 36+): $address"
                )
            }
        }
    }

    private fun handleDeviceEvent(type: String, address: String, logMessage: String) {
        val sanitized = sanitizeAddress(address)
        // CDM source: deduplicated via the shared DeviceEventGate (cross-source, CDM-priority)
        if (!DeviceEventGate.recordCdmEvent(sanitized, type)) return

        Timber.i(logMessage)

        if (type == "DeviceAppeared") {
            startForegroundServicePromotion()
        }
        ProgressEvents.onNext(type, sanitized)
    }

    private fun startForegroundServicePromotion() {
        val channelId = "gshock_companion_channel"
        val channel = NotificationChannel(
            channelId,
            "Device Connection",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Watch Connecting")
                .setContentText("Keeping connection alive...")
                .setSmallIcon(R.drawable.ic_watch_later_black_24dp)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        1983,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(1983, notification)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start foreground service")
            }
    }
}
