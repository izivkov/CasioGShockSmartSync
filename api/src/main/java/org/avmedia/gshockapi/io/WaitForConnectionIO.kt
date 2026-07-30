package org.avmedia.gshockapi.io

import CachedIO
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.utils.WatchDataListener

// ============================================================================
// Pure Functional Core: Connection State Utilities
// ============================================================================

/**
 * Pure functional core for connection state evaluation.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles connection status checks and response generation.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WaitForConnectionIOFunctional {
    /**
     * Pure validator: Determines if connection is already established.
     * 
     * Returns success response if connected, otherwise indicates waiting state.
     * No side effects - pure evaluation using Connection state.
     */

    fun checkConnectionAlreadyEstablished(): String? =
        if (Connection.isConnected()) "OK" else null

    /**
     * Pure validator: Determines if connection attempt is already in progress.
     * 
     * Returns true if Connection is already connecting to avoid duplicate attempts.
     * No side effects - pure state check.
     */
    fun isConnectionAlreadyInProgress(): Boolean = Connection.isConnecting()

    /**
     * Pure builder: Creates success response.
     * 
     * No side effects - returns constant value.
     */
    fun getSuccessResponse(): String = "OK"
}

@RequiresApi(Build.VERSION_CODES.O)
object WaitForConnectionIO {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(
        context: Context,
        deviceId: String?,
    ): String = waitForConnection(context, deviceId)

    private suspend fun waitForConnection(
        context: Context,
        deviceId: String?,
    ): String {
        WaitForConnectionIOFunctional.checkConnectionAlreadyEstablished()?.let { return it }

        //state.deferredResult?.cancel()
        state = state.copy(deferredResult = CompletableDeferred())

        setupConnectionListener()  // MUST be before startConnection to avoid race

        if (!WaitForConnectionIOFunctional.isConnectionAlreadyInProgress()) {
            WatchDataListener.init()
            Connection.startConnection(context, deviceId)
        }
        // If already connecting, just fall through and wait for the event

        return state.deferredResult?.await() ?: ""
    }

    private fun setupConnectionListener() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                CachedIO.clearCache()
                state.deferredResult?.complete(WaitForConnectionIOFunctional.getSuccessResponse())
                state = State()
            }
        )
        ProgressEvents.subscriber.runEventActions(this.javaClass.name, eventActions)
    }
}
