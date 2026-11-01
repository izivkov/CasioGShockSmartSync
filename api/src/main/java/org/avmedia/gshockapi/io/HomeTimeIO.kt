package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
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
     * Converts raw city data to ASCII string using the provided offset.
     * No side effects - pure string transformation.
     */
    fun parseHomeCity(data: String, offset: Int): String {
        if (data.isBlank()) return "N/A"
        val name = Utils.toAsciiString(data, offset)
        return if (name.isBlank() || name.all { it == 'ÿ' }) "N/A" else name
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Home Time IO handler with state management.
 * 
 * Provides access to the primary home city timezone (Register 0x24).
 * Uses pure functional core for data parsing.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(): String {
        return WatchInfo.protocol.getHomeTime()
    }

    suspend fun requestRaw(slot: Int): String {
        val key = "240$slot"
        
        return CachedIO.request(key) { k ->
            val deferred = CompletableDeferred<String>()
            synchronized(this) {
                state = state.copy(deferredResult = deferred)
            }
            IO.request(k)
            deferred.await()
        }
    }

    fun onReceived(data: String) {
        synchronized(this) {
            state.deferredResult?.complete(data)
            state = state.copy(deferredResult = null)
        }
    }
}
