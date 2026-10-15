package org.avmedia.gshockGoogleSync.pairing

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionDeviceCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: GShockRepository
) {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeScanAddresses: Set<String>? = null

    init {
        ProgressEvents.runEventActions(
            Utils.AppHashCode(),
            arrayOf(
                EventAction("DeviceAppeared") {
                    val address = ProgressEvents.getPayload("DeviceAppeared") as? String
                    if (address.isNullOrBlank()) {
                        Timber.e("DeviceAppeared triggered without a valid address")
                    } else {
                        val normalizedAddress = address.uppercase()
                        Timber.i("Device appeared: $normalizedAddress")
                        coordinatorScope.launch {
                            Timber.i("Stopping foreground BLE discovery before connecting to $normalizedAddress")
                            repository.stopScan()
                            if (repository.isConnected()) {
                                Timber.i("Device already connected. Skipping wait.")
                                return@launch
                            }

                            repeat(CONNECTION_ATTEMPTS) { attempt ->
                                try {
                                    Timber.i("$normalizedAddress waitForConnection attempt ${attempt + 1}/$CONNECTION_ATTEMPTS")
                                    repository.waitForConnection(normalizedAddress)
                                    return@launch
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    if (attempt == CONNECTION_ATTEMPTS - 1) {
                                        Timber.e(error, "Connection attempts exhausted for $normalizedAddress")
                                    } else {
                                        Timber.w(error, "Connection attempt failed for $normalizedAddress; retrying shortly")
                                        repository.disconnect()
                                        delay(CONNECTION_RETRY_DELAY_MS)
                                    }
                                }
                            }
                        }
                    }
                },
                EventAction("DeviceDisappeared") {
                    val address = ProgressEvents.getPayload("DeviceDisappeared") as? String
                    if (address.isNullOrBlank()) {
                        Timber.e("DeviceDisappeared triggered without a valid address")
                    } else {
                        Timber.i("Device disappeared: $address")
                    }
                }
            )
        )
    }

    suspend fun init() {
        cleanupLocalStorage()
        syncAssociations()
    }

    private suspend fun cleanupLocalStorage() {
        val repositoryAssociations = repository.getAssociations(context)
        val localAddresses = LocalDataStorage.getDeviceAddresses(context)
        localAddresses.filter { it !in repositoryAssociations }.forEach { address ->
            Timber.i("Cleaning up orphaned local association: $address")
            LocalDataStorage.removeDeviceAddress(context, address)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("NewApi")
    suspend fun syncAssociations() {
        val systemAssociations = repository.getAssociationsWithNames(context)
        systemAssociations.forEach { association ->
            LocalDataStorage.addDeviceAddress(context, association.address)
            association.name?.let { LocalDataStorage.setDeviceName(context, association.address, it) }
        }

        if (LocalDataStorage.get(context, "LastDeviceAddress", "").isNullOrEmpty()) {
            systemAssociations.firstOrNull()?.let {
                LocalDataStorage.put(context, "LastDeviceAddress", it.address)
            }
        }

        // CDM is authoritative for newly paired devices; local storage updates may
        // still be in flight when this synchronization runs.
        val activeAddresses = (
            LocalDataStorage.getDeviceAddresses(context) + systemAssociations.map { it.address }
        ).map { it.uppercase() }.toSet()

        if (!isLocationEnabled()) {
            Timber.w("Location Services disabled — notifying user.")
            ProgressEvents.onNext("LocationServicesDisabled")
            return
        }

        val cdm = context.getSystemService(android.companion.CompanionDeviceManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.myAssociations.forEach { associationInfo ->
                val address = associationInfo.deviceMacAddress?.toString()?.uppercase() ?: return@forEach
                manageDevicePresence(cdm, address, address in activeAddresses)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val systemAddresses = cdm.associations.map { it.uppercase() }.toSet()
            systemAddresses.forEach { address ->
                val shouldObserve = address in activeAddresses
                try {
                    if (shouldObserve) {
                        repository.startObservingDevicePresence(context, address)
                        Timber.d("CDM presence started (legacy) for $address")
                    } else {
                        @Suppress("DEPRECATION")
                        cdm.stopObservingDevicePresence(address)
                        Timber.d("CDM presence stopped (legacy) for $address")
                    }
                } catch (error: Exception) {
                    if (shouldObserve) Timber.e(error, "Legacy presence failed for $address")
                }
            }
        } else {
            Timber.i("CDM presence observation not available on API ${Build.VERSION.SDK_INT}, relying on fallback scan")
        }

        startFallbackScan(activeAddresses.toList())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startForegroundScan(activeAddresses)
        }
    }

    @SuppressLint("NewApi")
    private fun manageDevicePresence(
        cdm: android.companion.CompanionDeviceManager,
        address: String,
        shouldObserve: Boolean
    ) {
        try {
            if (shouldObserve) {
                repository.startObservingDevicePresence(context, address)
                Timber.d("CDM presence started for $address")
            } else {
                @Suppress("DEPRECATION")
                cdm.stopObservingDevicePresence(address)
                Timber.d("CDM presence stopped for $address")
            }
        } catch (error: Throwable) {
            if (shouldObserve) Timber.e(error, "CDM presence management failed for $address")
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
        val normalizedAddresses = addresses.map { it.uppercase() }.toSet()
        if (activeScanAddresses == normalizedAddresses) {
            Timber.d("Fallback scan already running for these addresses. Skipping restart.")
            return
        }

        val intent = Intent(context, BleScanReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
        repository.startFallbackScan(context, addresses, pendingIntent)
        activeScanAddresses = normalizedAddresses.takeIf { addresses.isNotEmpty() }
    }

    private fun startForegroundScan(activeAddresses: Set<String>) {
        if (activeAddresses.isEmpty()) {
            repository.stopScan()
            return
        }

        Timber.i("Starting foreground BLE discovery fallback for API ${Build.VERSION.SDK_INT}")
        repository.scan(
            context,
            filter = { device -> device.address.uppercase() in activeAddresses },
            onDeviceFound = { device ->
                val address = device.address.uppercase()
                if (CompanionDeviceEventGate.recordBleEvent(address, "DeviceAppeared")) {
                    Timber.i("Foreground scan found paired device: $address")
                    ProgressEvents.onNext("DeviceAppeared", address)
                }
            }
        )
    }

    private companion object {
        const val CONNECTION_ATTEMPTS = 2
        const val CONNECTION_RETRY_DELAY_MS = 1_000L
    }
}

object CompanionDeviceEventGate {
    private const val DEDUP_WINDOW_MS = 5_000L
    private val lastEmittedTime = mutableMapOf<Pair<String, String>, Long>()
    private val cdmLastFiredTime = mutableMapOf<Pair<String, String>, Long>()

    @Synchronized
    fun recordCdmEvent(address: String, eventType: String): Boolean {
        val key = address to eventType
        val now = System.currentTimeMillis()
        if (now - (lastEmittedTime[key] ?: 0L) < DEDUP_WINDOW_MS) {
            Timber.d("CompanionDeviceEventGate: suppressing duplicate CDM $eventType for $address")
            return false
        }
        lastEmittedTime[key] = now
        cdmLastFiredTime[key] = now
        return true
    }

    @Synchronized
    fun recordBleEvent(address: String, eventType: String): Boolean {
        val key = address to eventType
        val now = System.currentTimeMillis()
        if (now - (cdmLastFiredTime[key] ?: 0L) < DEDUP_WINDOW_MS) {
            Timber.d("CompanionDeviceEventGate: suppressing BLE $eventType for $address (CDM already active)")
            return false
        }
        if (now - (lastEmittedTime[key] ?: 0L) < DEDUP_WINDOW_MS) {
            Timber.d("CompanionDeviceEventGate: suppressing duplicate BLE $eventType for $address")
            return false
        }
        lastEmittedTime[key] = now
        return true
    }
}