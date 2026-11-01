package org.avmedia.gshockapi.ble

import android.Manifest
import android.annotation.SuppressLint
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.avmedia.gshockapi.IGShockAPI
import java.util.UUID
import java.util.regex.Pattern

object GShockPairingManager {
    private val CASIO_SERVICE_UUID: UUID =
        UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")

    // Constant for the name pattern to avoid hardcoded literals
    private const val DEVICE_NAME_PATTERN: String = "CASIO.*"

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

        val deviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(DEVICE_NAME_PATTERN, Pattern.CASE_INSENSITIVE))
            .build()

        val builder = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)

        val pairingRequest = builder.build()

        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("Deprecated in Java")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                onChooserReady(chooserLauncher)
            }

            override fun onFailure(error: CharSequence?) {
                val errorMessage: String = error?.toString() ?: "Companion device pairing failed"
                onError(errorMessage)
            }
        }

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
            deviceManager.myAssociations.map { info ->
                IGShockAPI.Association(
                    info.deviceMacAddress?.toString() ?: "",
                    info.displayName?.toString()
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val associations = deviceManager.associations
            associations.map { info ->
                IGShockAPI.Association(info, null)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun disassociate(context: Context, address: String) {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association = deviceManager.myAssociations.find {
                    it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
                }
                if (association != null) {
                    deviceManager.disassociate(association.id)
                    return
                }
            }

            // Fallback for API < 33 or if association info was not found
            @Suppress("DEPRECATION")
            deviceManager.disassociate(address)
        } catch (e: Exception) {
            // Log error if needed, but keeping your logic of ignoring
        }
    }

    @RequiresPermission(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE)
    @RequiresApi(Build.VERSION_CODES.S)
    fun startObservingDevicePresence(context: Context, address: String) {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return

        // Baklava (API 36) introduces more structured presence requests
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            startObservingApi36(deviceManager, address)
        } else {
            @Suppress("DEPRECATION")
            deviceManager.startObservingDevicePresence(address)
        }
    }

    @RequiresPermission(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE)
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun startObservingApi36(
        deviceManager: CompanionDeviceManager,
        address: String
    ) {
        val association = deviceManager.myAssociations.firstOrNull {
            it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
        } ?: return

        val request = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(association.id)
            .build()

        deviceManager.startObservingDevicePresence(request)
    }

    @RequiresPermission(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE)
    @RequiresApi(Build.VERSION_CODES.S)
    fun stopObservingDevicePresence(context: Context, address: String) {
        val deviceManager: CompanionDeviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                ?: return

        // Sync with start logic: Use the modern Request-based API for Baklava+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val association = deviceManager.myAssociations.firstOrNull {
                it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
            } ?: return

            val request: ObservingDevicePresenceRequest = ObservingDevicePresenceRequest.Builder()
                .setAssociationId(association.id)
                .build()

            deviceManager.stopObservingDevicePresence(request)
        } else {
            // Use legacy String address for API 31-35
            @Suppress("DEPRECATION")
            deviceManager.stopObservingDevicePresence(address)
        }
    }
}