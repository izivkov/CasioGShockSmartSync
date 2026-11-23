package org.avmedia.gshockGoogleSync.pairing

import org.avmedia.gshockGoogleSync.session.BackgroundConnection
import kotlinx.coroutines.isActive
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
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKGROUND_INTERVAL_MS = 5 * 60_000L
private const val BACKGROUND_WINDOW_MS = 60_000L

@Singleton
class CompanionDeviceCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: GShockRepository
) {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeScanAddresses: Set<String>? = null

    /** Every interval, opens a short background window for each always-connected watch. */
    private fun startBackgroundArming() {
        coordinatorScope.launch {
            while (isActive) {
                delay(BACKGROUND_INTERVAL_MS)
                activeScanAddresses.orEmpty().forEach { address ->
                    val name = LocalDataStorage.getDeviceName(context, address)?.takeIf { it.isNotBlank() }
                        ?: return@forEach
                    if (org.avmedia.gshockGoogleSync.session.WatchModelOverride.isAlwaysConnected(name)) {
                        BackgroundReadPermission.arm(address, BACKGROUND_WINDOW_MS)
                    }
                }
            }
        }
    }

    init {
        startBackgroundArming()
        ProgressEvents.subscribe(
            arrayOf(
                EventAction("DeviceAppeared") {
                    val address = ProgressEvents.getPayload("DeviceAppeared") as? String
                    if (address.isNullOrBlank()) {
                        Timber.e("DeviceAppeared triggered without a valid address")
                    } else {
                        val normalizedAddress = address.uppercase()
                        Timber.i("Device appeared: $normalizedAddress")
                        coordinatorScope.launch {
                            // Our own gate is gone: an always-connected watch is always admitted. A background window only marks the read.
                            val background = BackgroundReadPermission.consume(normalizedAddress)
                            Timber.i("Stopping foreground BLE discovery before connecting to $normalizedAddress")
                            repository.stopScan()
                            // Only this watch counts. Any other connected watch must not block this one.
                            if (repository.isConnectedTo(normalizedAddress)) {
                                Timber.i("Device already connected. Skipping wait.")
                                return@launch
                            }

                            if (background) BackgroundConnection.active = true

                            // Like the official app: while the watch is advertising, retry quickly. A refused
                            // attempt fails within about 0.1 s, so a short interval is enough.
                            val windowEnd = System.currentTimeMillis() + CONNECTION_WINDOW_MS
                            var attempt = 0
                            while (System.currentTimeMillis() < windowEnd && !repository.isConnectedTo(normalizedAddress)) {
                                attempt++
                                try {
                                    Timber.i("$normalizedAddress connection attempt $attempt")
                                    repository.waitForConnection(normalizedAddress)
                                    // The watch that just connected is the one the screen shows after it disconnects.
                                    org.avmedia.gshockGoogleSync.utils.LocalDataStorage.put(
                                        context, "LastDeviceName", org.avmedia.gshockapi.WatchInfo.name
                                    )
                                    // No read and no release here: BackgroundHealthSync reads the LifeLog of every connection
                                    // and the keep-alive holds the watch (a second reader plus a release cuts the link).
                                    BackgroundConnection.active = false
                                    return@launch
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Timber.w("Attempt $attempt refused for $normalizedAddress: ${error.message}")
                                    repository.disconnect()
                                    delay(CONNECTION_RETRY_DELAY_MS)
                                }
                            }
                            BackgroundConnection.active = false
                            Timber.i("Advertising window closed without a connection for $normalizedAddress")
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
        const val CONNECTION_RETRY_DELAY_MS = 200L
        const val CONNECTION_WINDOW_MS = 120_000L
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