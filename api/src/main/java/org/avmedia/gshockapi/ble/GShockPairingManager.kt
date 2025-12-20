package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.companion.AssociationInfo
import android.os.Build
import android.os.ParcelUuid
import org.avmedia.gshockapi.IGShockAPI
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
        return getAssociationsWithNames(context).map { it.address }
    }

    @SuppressLint("NewApi", "MissingPermission")
    fun getAssociationsWithNames(context: Context): List<IGShockAPI.Association> {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return emptyList()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            deviceManager.myAssociations.map {
                IGShockAPI.Association(it.deviceMacAddress.toString(), it.displayName?.toString())
            }
        } else {
            @Suppress("DEPRECATION")
            val associations = deviceManager.associations
            associations.map { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info is AssociationInfo) {
                    IGShockAPI.Association(
                        info.deviceMacAddress.toString(),
                        info.displayName?.toString()
                    )
                } else {
                    IGShockAPI.Association(info.toString(), null)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    fun disassociate(context: Context, address: String) {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association =
                    deviceManager.myAssociations.find { it.deviceMacAddress.toString() == address }
                if (association != null) {
                    deviceManager.disassociate(association.id)
                    return
                }
            }
            @Suppress("DEPRECATION")
            deviceManager.disassociate(address)
        } catch (e: Exception) {
            // ignore
        }
    }
}
