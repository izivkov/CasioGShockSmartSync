package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import java.util.UUID
import java.util.regex.Pattern

object GShockPairingManager {

    private val CASIO_SERVICE_UUID: UUID =
        UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission", "NewApi")
    fun associate(
        context: Context,
        onChooserReady: (IntentSender) -> Unit,
        onError: (String) -> Unit
    ) {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: run {
                    onError("CompanionDeviceManager not available")
                    return
                }

        val deviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("CASIO.*", Pattern.CASE_INSENSITIVE))
            // .addServiceUuid(ParcelUuid(CASIO_SERVICE_UUID), null)
            .build()

        val builder = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setSingleDevice(true)
        }

        val pairingRequest = builder.build()

        val callback = object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                // This is called when the system is ready to show the pairing dialog.
                // For setSingleDevice(false), this often happens immediately.
                onChooserReady(chooserLauncher)
            }

            override fun onFailure(error: CharSequence?) {
                onError(error?.toString() ?: "Companion device pairing failed")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            deviceManager.associate(pairingRequest, context.mainExecutor, callback)
        } else {
            deviceManager.associate(pairingRequest, callback, null)
        }
    }

    @SuppressLint("NewApi")
    fun getAssociations(context: Context): List<String> {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return emptyList()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            deviceManager.associations
        } else {
            @Suppress("DEPRECATION")
            deviceManager.associations
        }
    }
}
