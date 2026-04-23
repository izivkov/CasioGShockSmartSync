package org.avmedia.gshockGoogleSync.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import timber.log.Timber

@Singleton
class ReconnectManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repository: GShockRepository
) {
    private companion object {
        const val DEVICE_APPEAR_DEBOUNCE_MS = 5_000L
        const val RETRY_SCAN_WINDOW_MS = 5 * 60_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()

    private var foregroundCount = 0
    private var retryScanUntilMs = 0L
    private var currentConnectJob: Job? = null
    private var currentConnectAddress: String? = null
    private var lastConnectStartedAtMs = 0L
    private var retryTimerJob: Job? = null

    fun onAppForegroundChanged(isForeground: Boolean) {
        synchronized(stateLock) {
            foregroundCount = if (isForeground) {
                foregroundCount + 1
            } else {
                (foregroundCount - 1).coerceAtLeast(0)
            }
        }
        updateScanPolicy("app_foreground_changed")
    }

    fun onKnownDevicesChanged() {
        updateScanPolicy("known_devices_changed")
    }

    fun requestManualReconnect(address: String) {
        synchronized(stateLock) {
            retryScanUntilMs = SystemClock.elapsedRealtime() + RETRY_SCAN_WINDOW_MS
        }
        Timber.i("Manual reconnect requested for %s", address.uppercase())
        updateScanPolicy("manual_reconnect")
    }

    fun onConnected() {
        Timber.i("ReconnectManager observed active connection")
        updateScanPolicy("connected")
    }

    fun onConnectionFailure() {
        synchronized(stateLock) {
            retryScanUntilMs = SystemClock.elapsedRealtime() + RETRY_SCAN_WINDOW_MS
        }
        Timber.w("Reconnect retry window opened after connection failure")
        updateScanPolicy("connection_failed")
    }

    fun onDisconnect(address: String?) {
        val normalizedAddress = address?.uppercase()
        synchronized(stateLock) {
            retryScanUntilMs = SystemClock.elapsedRealtime() + RETRY_SCAN_WINDOW_MS
            if (currentConnectAddress.equals(normalizedAddress, ignoreCase = true)) {
                currentConnectJob?.cancel()
                currentConnectJob = null
            }
        }
        Timber.i(
            "Reconnect retry window opened for %s",
            normalizedAddress ?: "<unknown>"
        )
        updateScanPolicy("disconnect")
    }

    fun onDeviceAppeared(address: String) {
        val normalizedAddress = address.uppercase()
        if (!isKnownDevice(normalizedAddress)) {
            Timber.i("Ignoring appearance for unknown device %s", normalizedAddress)
            return
        }

        val shouldStart = synchronized(stateLock) {
            val now = SystemClock.elapsedRealtime()
            when {
                currentConnectJob?.isActive == true -> false
                currentConnectAddress.equals(normalizedAddress, ignoreCase = true) &&
                    now - lastConnectStartedAtMs < DEVICE_APPEAR_DEBOUNCE_MS -> false
                else -> {
                    currentConnectAddress = normalizedAddress
                    lastConnectStartedAtMs = now
                    true
                }
            }
        }

        if (!shouldStart) {
            Timber.i("Skipping duplicate connection attempt for %s", normalizedAddress)
            return
        }

        val job = scope.launch {
            try {
                Timber.i("%s waitForConnection", normalizedAddress)
                repository.waitForConnection(normalizedAddress)
            } catch (e: Exception) {
                Timber.e(e, "Error occurred while waiting for connection to %s", normalizedAddress)
            } finally {
                synchronized(stateLock) {
                    if (currentConnectAddress.equals(normalizedAddress, ignoreCase = true)) {
                        currentConnectJob = null
                    }
                }
            }
        }

        synchronized(stateLock) {
            currentConnectJob = job
        }
    }

    private fun updateScanPolicy(reason: String) {
        val knownAddresses = getKnownAddresses()
        val now = SystemClock.elapsedRealtime()
        val shouldScan = synchronized(stateLock) {
            val foregroundActive = foregroundCount > 0
            !repository.isConnected() &&
                (foregroundActive || (retryScanUntilMs > now && knownAddresses.isNotEmpty()))
        } && knownAddresses.isNotEmpty()

        if (shouldScan && !hasScanPermission()) {
            Timber.w(
                "Skipping scan policy update (%s): BLUETOOTH_SCAN permission is not granted",
                reason
            )
            stopRetryTimer()
            stopScanService()
            return
        }

        if (shouldScan) {
            Timber.i(
                "ReconnectManager enabling scan policy (%s) for %d known device(s)",
                reason,
                knownAddresses.size
            )
            startScanService()
            scheduleRetryStopIfNeeded()
        } else {
            Timber.i("ReconnectManager disabling scan policy (%s)", reason)
            stopRetryTimer()
            stopScanService()
        }
    }

    private fun scheduleRetryStopIfNeeded() {
        stopRetryTimer()

        val remainingMs = synchronized(stateLock) {
            if (foregroundCount > 0) {
                null
            } else {
                (retryScanUntilMs - SystemClock.elapsedRealtime()).takeIf { it > 0L }
            }
        } ?: return

        retryTimerJob = scope.launch {
            delay(remainingMs)
            updateScanPolicy("retry_window_expired")
        }
    }

    private fun stopRetryTimer() {
        retryTimerJob?.cancel()
        retryTimerJob = null
    }

    private fun getKnownAddresses(): Set<String> =
        (repository.getAssociations(appContext) + LocalDataStorage.getDeviceAddresses(appContext))
            .map { it.uppercase() }
            .toSet()

    private fun isKnownDevice(address: String): Boolean =
        getKnownAddresses().contains(address.uppercase())

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

    private fun startScanService() {
        appContext.startService(Intent(appContext, GShockScanService::class.java))
    }

    private fun stopScanService() {
        appContext.stopService(Intent(appContext, GShockScanService::class.java))
    }
}
