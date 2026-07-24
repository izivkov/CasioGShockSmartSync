package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Watch Name Extraction
// ============================================================================

/**
 * Pure functional core for watch name processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles watch name parsing and sanitization.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WatchNameIOFunctional {
    /**
     * Pure parser: Extracts and sanitizes watch name from data.
     * 
     * Converts raw data (starting at index 1) to ASCII string,
     * then removes non-ASCII characters.
     * No side effects - pure string transformation.
     * 
     * @param data Raw data string from watch
     * @return Sanitized watch name
     */
    fun parseWatchName(data: String): Result<String> = runCatching {
        if (data.isEmpty()) {
            throw IllegalArgumentException("Empty watch name data")
        }
        val asciiName = Utils.toAsciiString(data, 1)
        Utils.trimNonAsciiCharacters(asciiName)
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Watch Name IO handler with state management.
 * 
 * Retrieves the user-assigned name of the watch.
 * Uses pure functional core for data parsing and sanitization.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WatchNameIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(): String =
        CachedIO.request("23") { key -> getWatchName(key) }

    private suspend fun getWatchName(key: String): String {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: ""
    }

    fun onReceived(data: String) {
        // Use pure function to parse watch name
        WatchNameIOFunctional.parseWatchName(data)
            .fold(
                onSuccess = { watchName ->
                    state.deferredResult?.complete(watchName)
                    state = State()
                },
                onFailure = { error ->
                    Timber.e("Failed to parse watch name: ${error.message}")
                    state.deferredResult?.complete("")
                    state = State()
                }
            )
    }
}
