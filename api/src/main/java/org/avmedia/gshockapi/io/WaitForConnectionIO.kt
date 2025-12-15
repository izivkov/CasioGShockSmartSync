package org.avmedia.gshockapi.io

import CachedIO
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.utils.WatchDataListener

@RequiresApi(Build.VERSION_CODES.O)
object WaitForConnectionIO {
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
        if (Connection.isConnected() || Connection.isConnecting()) {
            return "Connecting"
        }

        state = state.copy(deferredResult = CompletableDeferred())
        WatchDataListener.init()
        Connection.startConnection(context, deviceId)

        setupConnectionListener()
        return state.deferredResult?.await() ?: ""
    }

    private fun setupConnectionListener() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                CachedIO.clearCache()
                state.deferredResult?.complete("OK")
                state = State()
            }
        )

        ProgressEvents.subscriber.runEventActions(this.javaClass.name, eventActions)
    }
}
