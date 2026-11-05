package org.avmedia.gshockapi.io

// ============================================================================
// Pure Functional Core: Unknown State Handling
// ============================================================================

/**
 * Pure functional core for unknown state operations.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles unknown message responses.
 */
object UnknownIOFunctional {
    /**
     * Pure provider: Returns constant unknown response.
     * 
     * No side effects - returns constant value.
     */
    fun getUnknownResponse(): String = "UNKNOWN"
}

object UnknownIO {

    suspend fun request(): String = UnknownIOFunctional.getUnknownResponse()

    @Suppress("UNUSED_PARAMETER")
    fun onReceived(message: String) {
        // NO-OP
    }
}