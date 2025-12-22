package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.AssociationInfo
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.IGShockAPI
import java.util.UUID
import java.util.regex.Pattern

object GShockPairingManager {
    private val CASIO_SERVICE_UUID: UUID =
        UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
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

        // This class exists from API 26, so you can use it from Android 9+.
        val deviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("CASIO.*", Pattern.CASE_INSENSITIVE))
            // If you want to filter by service UUID and the device is BLE, you can
            // switch to BluetoothLeDeviceFilter here (API 26+). [web:3][web:10]
            // .addServiceUuid(ParcelUuid(CASIO_SERVICE_UUID), null)
            .build()

        val builder = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)

        builder.setSingleDevice(true)

        val pairingRequest = builder.build()

        val callback = @RequiresApi(Build.VERSION_CODES.O)
        object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                onChooserReady(chooserLauncher)
            }

            override fun onFailure(error: CharSequence?) {
                onError(error?.toString() ?: "Companion device pairing failed")
            }
        }

        // New associate() overload with Executor is from API 33 (Tiramisu). [web:1][web:10]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            deviceManager.associate(pairingRequest, context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            deviceManager.associate(pairingRequest, callback, null)
        }
    }

    fun getAssociations(context: Context): List<String> {
        return getAssociationsWithNames(context).map { it.address }
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun getAssociationsWithNames(context: Context): List<IGShockAPI.Association> {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return emptyList()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Modern API returning AssociationInfo objects. [web:1]
            deviceManager.myAssociations.map {
                IGShockAPI.Association(
                    it.deviceMacAddress?.toString() ?: "",
                    it.displayName?.toString()
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val associations = deviceManager.associations
            // On Android 9–12 this returns List<String> of association IDs. [web:1][web:10]
            associations.map { info ->
                // On Android 12 (S)–12L (Sv2) you can sometimes get AssociationInfo in APIs,
                // but for the CompanionDeviceManager.associations list it is just String,
                // so treat it as an address/id string here.
                IGShockAPI.Association(info, null)
            }
        }
    }

    fun disassociate(context: Context, address: String) {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Newer API uses association id from AssociationInfo. [web:1]
                val association =
                    deviceManager.myAssociations.find { it.deviceMacAddress?.toString() == address }
                if (association != null) {
                    deviceManager.disassociate(association.id)
                    return
                }
            }

            // On Android 9–12 use the deprecated String-based disassociate(). [web:10]
            @Suppress("DEPRECATION")
            deviceManager.disassociate(address)
        } catch (_: Exception) {
            // ignore
        }
    }
}
