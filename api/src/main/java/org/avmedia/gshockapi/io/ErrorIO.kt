package org.avmedia.gshockapi.io

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

object ErrorIO {
    private data class State(
        val error: String = ""
    )

    private var state = State()

    suspend fun request(): String = ErrorIOFunctional.getErrorOrDefault(state.error)

    fun onReceived(data: String) {
        state = state.copy(error = data)
        Timber.d("ErrorIO: onReceived: $data")
    }
}
