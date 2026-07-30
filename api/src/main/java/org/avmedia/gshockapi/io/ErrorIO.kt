package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Error State Management
// ============================================================================

/**
 * Pure functional core for error state operations.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles error message validation and retrieval.
 */
object ErrorIOFunctional {
    /**
     * Pure validator: Returns the error message or default.
     * 
     * No side effects - simple data accessor.
     */
    fun getErrorOrDefault(error: String): String = error.ifEmpty { "ERROR" }
}

@RequiresApi(Build.VERSION_CODES.O)
object ErrorIO {
    private data class State(
        val error: String = ""
    )

    private var state = State()

    suspend fun request(): String = ErrorIOFunctional.getErrorOrDefault(state.error)

    fun onReceived(data: String) {
        state = state.copy(error = data)
        Timber.d("ErrorIO: onReceived: $data")

        if (data.contains("81 13")) {
            SettingsIO.onRunError()
        }
        if (data.contains("81 11")) {
            TimeAdjustmentIO.onRunError()
        }
    }
}
