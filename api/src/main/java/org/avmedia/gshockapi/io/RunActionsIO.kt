package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Run Actions Detection
// ============================================================================

/**
 * Pure functional core for run actions processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles run actions trigger detection.
 */
@RequiresApi(Build.VERSION_CODES.O)
object RunActionsIOFunctional {
    /**
     * Pure validator: Checks if the run actions trigger signal was received.
     * 
     * Protocol: Always-connected watches emit "0x0A 02" when user interacts.
     * This signal indicates that the watch wants the app to run synchronization actions
     * (set time, update calendar, etc).
     * 
     * No side effects - simple comparison.
     */
    fun isRunActionsTrigger(data: String): Boolean =
        data == "0x0A 02" && WatchInfo.alwaysConnected
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Run Actions IO handler.
 * 
 * Detects when an always-connected watch triggers the run actions signal.
 * This causes the app to synchronize time, calendar, and other data with the watch.
 * Uses pure functional core for trigger detection.
 */
@RequiresApi(Build.VERSION_CODES.O)
object RunActionsIO {
    private data class State(
        val lastRequest: String = ""
    )

    private var state = State()

    suspend fun request(): String {
        state = state.copy(lastRequest = "RUN_ACTIONS")
        return state.lastRequest
    }

    fun onReceived(data: String) {
        // Use pure function to check if this is a run actions trigger
        if (RunActionsIOFunctional.isRunActionsTrigger(data)) {
            Timber.i("Run actions triggered by always-connected watch")
            ProgressEvents.onNext("RunActions")
        }
    }
}
