package org.avmedia.gshockGoogleSync.pairing

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.CrashReportHelper
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceAssociationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: GShockRepository
) {
    var pendingCrashLog by mutableStateOf<String?>(null)
    private var activeScanAddresses: Set<String>? = null

    suspend fun init() {
        recoverFromPairingCrash()
        cleanupLocalStorage()
        syncAssociations()
    }

    /** Recover from a previous pairing crash by clearing potentially corrupted state */
    private suspend fun recoverFromPairingCrash() {
        try {
            if (CrashReportHelper.hasPairingCrashFlag(context)) {
                Timber.w("Detected previous pairing crash, attempting recovery")

                val latestCrash = CrashReportHelper.getLatestCrashLog(context)

                // Set the pending log to be picked up by the UI
                if (latestCrash != null) {
                    pendingCrashLog = latestCrash
                    Timber.e("Previous crash log captured for display")
                }

                CrashReportHelper.clearPairingCrashFlag(context)
                Timber.i("Pairing crash recovery completed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to recover from pairing crash")
        }
    }

    suspend fun cleanupLocalStorage() {
        val repositoryAssociations = repository.getAssociations(context)
        val localAddresses = LocalDataStorage.getDeviceAddresses(context)

        // 1. Identify which addresses SHOULD be removed
        val addressesToRemove = localAddresses.filter { it !in repositoryAssociations }

        // 2. Remove them one by one (Safe here because we aren't modifying 'localAddresses')
        addressesToRemove.forEach { address ->
            Timber.i("Cleaning up orphaned local association: $address")
            LocalDataStorage.removeDeviceAddress(context, address)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("NewApi")
    fun syncAssociations() {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Get the list of associations the system knows about
            val systemAssociations = repository.getAssociationsWithNames(context)

            // 2. Auto-Discovery: Add any system associations to LocalDataStorage if not already there
            systemAssociations.forEach { association ->
                LocalDataStorage.addDeviceAddress(context, association.address)
                association.name?.let {
                    LocalDataStorage.setDeviceName(context, association.address, it)
                }
            }

            // 3. Update the "LastDeviceAddress" if none is set
            if (LocalDataStorage.get(context, "LastDeviceAddress", "").isNullOrEmpty()) {
                systemAssociations.firstOrNull()?.let {
                    LocalDataStorage.put(context, "LastDeviceAddress", it.address)
                }
            }

            // 4. Re-fetch the final list of active addresses after auto-discovery
            val finalActiveAddresses =
                LocalDataStorage.getDeviceAddresses(context)
                    .map { it.uppercase() }
                    .toSet()

            if (!isLocationEnabled()) {
                Timber.w("Location Services disabled — notifying user.")
                ProgressEvents.onNext("LocationServicesDisabled")
                return@launch
            }

            val cdm = context.getSystemService(android.companion.CompanionDeviceManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.forEach { associationInfo ->
                    val macAddress = associationInfo.deviceMacAddress?.toString()?.uppercase() ?: return@forEach
                    manageDevicePresence(cdm, associationInfo, macAddress, macAddress in finalActiveAddresses)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31-32: only string-based API available
                @Suppress("DEPRECATION")
                val systemMacs = cdm.associations.map { it.uppercase() }.toSet()

                systemMacs.forEach { mac ->
                    val shouldObserve = mac in finalActiveAddresses
                    try {
                        if (shouldObserve) {
                            repository.startObservingDevicePresence(context, mac)
                            Timber.d("CDM presence started (legacy) for $mac")
                        } else {
                            @Suppress("DEPRECATION")
                            cdm.stopObservingDevicePresence(mac)
                            Timber.d("CDM presence stopped (legacy) for $mac")
                        }
                    } catch (e: Exception) {
                        if (shouldObserve) Timber.e(e, "Legacy presence failed for $mac")
                    }
                }
            } else {
                Timber.i("CDM presence observation not available on API ${Build.VERSION.SDK_INT}, relying on fallback scan")
            }

            // Start fallback scan for all active addresses (runs on all API levels)
            startFallbackScan(finalActiveAddresses.toList())
        }
    }

    @SuppressLint("NewApi")
    private fun manageDevicePresence(
        cdm: android.companion.CompanionDeviceManager,
        associationInfo: android.companion.AssociationInfo,
        macAddress: String,
        shouldObserve: Boolean
    ) {
        try {
            // Preferred: associationId-based (API 33+, works on GrapheneOS)
            val request = android.companion.ObservingDevicePresenceRequest.Builder()
                .setAssociationId(associationInfo.id)
                .build()

            if (shouldObserve) {
                cdm.startObservingDevicePresence(request)
                Timber.d("CDM presence started (request-based) for $macAddress")
            } else {
                cdm.stopObservingDevicePresence(request)
                Timber.d("CDM presence stopped for $macAddress")
            }
        } catch (e: Throwable) {
            // Handle ROMs like LineageOS/crDroid that might report API 33 but lack the new method
            if (e is NoSuchMethodError || e is Exception) {
                Timber.w("Modern CDM method failed, falling back to address-based for $macAddress")
                try {
                    if (shouldObserve) {
                        repository.startObservingDevicePresence(context, macAddress)
                    } else {
                        @Suppress("DEPRECATION")
                        cdm.stopObservingDevicePresence(macAddress)
                    }
                } catch (e2: Exception) {
                    if (shouldObserve) Timber.e(e2, "Address-based CDM failed for $macAddress")
                }
            } else {
                throw e
            }
        }
    }

    @SuppressLint("NewApi")
    suspend fun checkPairedDevicesOrNotify() {
        val associations = repository.getAssociationsWithNames(context)
        if (associations.isEmpty() && LocalDataStorage.getDeviceAddresses(context).isEmpty()) {
            ProgressEvents.onNext("NoPairedDevices")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFallbackScan(addresses: List<String>) {
        val newAddressesSet = addresses.map { it.uppercase() }.toSet()

        // Optimization: If the addresses haven't changed, don't restart the scan
        if (activeScanAddresses == newAddressesSet) {
            Timber.d("Fallback scan already running for these addresses. Skipping restart.")
            return
        }

        // Create a PendingIntent to identify the scan
        val intent =
            Intent(context, org.avmedia.gshockGoogleSync.receivers.BleScanReceiver::class.java)
        val pendingIntent =
            android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_MUTABLE
            )

        repository.startFallbackScan(context, addresses, pendingIntent)

        activeScanAddresses = if (addresses.isEmpty()) {
            null
        } else {
            newAddressesSet
        }
    }
}
