package org.avmedia.gshockGoogleSync.utils

import timber.log.Timber

/**
 * Centralised gate for DeviceAppeared / DeviceDisappeared events.
 *
 * Two detection sources exist on Android 13+:
 *   - CDM (CompanionDeviceManager) presence observation  — preferred
 *   - BLE fallback scan (BleScanReceiver)                — safety net
 *
 * Rules enforced here:
 *  1. CDM events are deduplicated against themselves (same address+type within [DEDUP_WINDOW_MS]).
 *  2. BLE events are suppressed entirely if CDM has already fired for the same address+type
 *     within [DEDUP_WINDOW_MS], giving CDM priority.
 *  3. BLE events are deduplicated against themselves when CDM has NOT fired recently.
 *
 * Both [recordCdmEvent] and [recordBleEvent] return `true` only when the caller
 * should actually emit the event to ProgressEvents.
 */
object DeviceEventGate {

    private const val DEDUP_WINDOW_MS = 5_000L

    // Last time *any* source emitted an event for (address, eventType)
    private val lastEmittedTime = mutableMapOf<Pair<String, String>, Long>()

    // Last time CDM specifically fired for (address, eventType)
    private val cdmLastFiredTime = mutableMapOf<Pair<String, String>, Long>()

    /**
     * Called by the CDM source before emitting an event.
     * @return true if the event should be forwarded to ProgressEvents.
     */
    @Synchronized
    fun recordCdmEvent(address: String, eventType: String): Boolean {
        val key = address to eventType
        val now = System.currentTimeMillis()

        val lastEmitted = lastEmittedTime[key] ?: 0L
        if (now - lastEmitted < DEDUP_WINDOW_MS) {
            Timber.d("DeviceEventGate: suppressing duplicate CDM $eventType for $address")
            return false
        }

        lastEmittedTime[key] = now
        cdmLastFiredTime[key] = now
        return true
    }

    /**
     * Called by the BLE fallback scan source before emitting an event.
     * Suppressed entirely when CDM has fired recently (CDM takes priority).
     * @return true if the event should be forwarded to ProgressEvents.
     */
    @Synchronized
    fun recordBleEvent(address: String, eventType: String): Boolean {
        val key = address to eventType
        val now = System.currentTimeMillis()

        // CDM priority: suppress BLE if CDM fired recently for this address + event type
        val cdmTime = cdmLastFiredTime[key] ?: 0L
        if (now - cdmTime < DEDUP_WINDOW_MS) {
            Timber.d("DeviceEventGate: suppressing BLE $eventType for $address (CDM already active)")
            return false
        }

        // General BLE self-dedup
        val lastEmitted = lastEmittedTime[key] ?: 0L
        if (now - lastEmitted < DEDUP_WINDOW_MS) {
            Timber.d("DeviceEventGate: suppressing duplicate BLE $eventType for $address")
            return false
        }

        lastEmittedTime[key] = now
        return true
    }
}
