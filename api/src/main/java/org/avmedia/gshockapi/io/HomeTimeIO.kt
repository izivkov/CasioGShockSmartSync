package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils

// ============================================================================
// Pure Functional Core: Home Time Data Processing
// ============================================================================

/**
 * Pure functional core for home time processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles home city data transformations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIOFunctional {
    /**
     * Pure parser: Extracts home city name from world cities data.
     * 
     * Converts raw city data (starting at index 2) to ASCII string.
     * No side effects - pure string transformation.
     */
    fun parseHomeCity(data: String): String =
        Utils.toAsciiString(data, 2)
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Home Time IO handler with state management.
 * 
 * Provides access to the primary home city timezone.
 * Uses pure functional core for data parsing.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIO {
    private data class State(
        val homeCity: String = ""
    )

    private var state = State()

    suspend fun request(): String {
        // Use pure function to parse
        val homeCity = HomeTimeIOFunctional.parseHomeCity(
            WorldCitiesIO.request(0)
        )
        state = state.copy(homeCity = homeCity)
        return state.homeCity
    }

    fun onReceived(data: String) {
        // Use pure function to parse
        state = state.copy(homeCity = HomeTimeIOFunctional.parseHomeCity(data))
    }
}
