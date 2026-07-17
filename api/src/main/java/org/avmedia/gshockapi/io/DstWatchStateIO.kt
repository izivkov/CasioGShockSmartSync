package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

// ============================================================================
// Pure Functional Core: DST Watch State Operations
// ============================================================================

/**
 * Pure functional core for DST watch state processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles DST value extraction and manipulation.
 */
object DstWatchStateIOFunctional {
    /**
     * Pure transformer: Sets DST value in watch state.
     * 
     * Takes DST state string and DST value, updates byte at index 3.
     * No side effects - pure data transformation.
     * 
     * Protocol format (14 bytes):
     * 0x1d 0x00 0x01 DST0 DST1 TZ0A TZ0B TZ1A TZ1B ff ff ff ff ff
     * We modify DST0 (index 3) with the provided DST value.
     */
    fun setDST(dstState: String, dst: Int): String {
        val intArray = Utils.toIntArray(dstState)
        intArray[3] = dst

        val newValue = Utils.byteArrayOfIntArray(intArray.toIntArray())
        return Utils.fromByteArrayToHexStrWithSpaces(newValue)
    }
}

object DstWatchStateIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(state: IO.DstState): String =
        CachedIO.request("1d0${state.state}") { key ->
            getDSTWatchState(key)
        }

    private suspend fun getDSTWatchState(key: String): String {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: ""
    }

    /*
    There are six clocks on the Casio GW-B5600
    0 is the main clock
    1-5 are the world clocks

    0x1d 00 01 DST0 DST1 TZ0A TZ0B TZ1A TZ1B ff ff ff ff ff
    0x1d 02 03 DST2 DST3 TZ2A TZ2B TZ3A TZ3B ff ff ff ff ff
    0x1d 04 05 DST4 DST5 TZ4A TZ4B TZ5A TZ5B ff ff ff ff ff
    DST: bitwise flags; bit0: DST on, bit1: DST auto
    */

    suspend fun setDST(dstState: String, dst: Int): String =
        DstWatchStateIOFunctional.setDST(dstState, dst)

    fun onReceived(data: String) {
        state.deferredResult?.complete(data)

        // Do not reset state here, as it is used in the request function.
        // state = State()
    }
}