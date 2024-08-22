package org.avmedia.gshockapi.io

import android.bluetooth.BluetoothDevice
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

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<String>
    }

    suspend fun request(
        context: Context,
        deviceId: String?,
        deviceName: String?
    ): String {
        return waitForConnection(context, deviceId, deviceName)
    }

    private suspend fun waitForConnection(
        context: Context,
        deviceId: String?,
        deviceName: String?
    ): String {

        if (Connection.isConnected() || Connection.isConnecting()) {
            return "Connecting"
        }

        DeferredValueHolder.deferredResult = CompletableDeferred()
        WatchDataListener.init()

        Connection.startConnection(context, deviceId, deviceName)

        fun waitForConnectionSetupComplete() {
            val eventActions = arrayOf(
                EventAction("ConnectionSetupComplete") {
                    val device =
                        ProgressEvents.getPayload("ConnectionSetupComplete") as BluetoothDevice

                    CachedIO.clearCache()
                    DeferredValueHolder.deferredResult.complete("OK")
                },
            )

            ProgressEvents.subscriber.runEventActions(this.javaClass.name, eventActions)
        }

        waitForConnectionSetupComplete()
        return DeferredValueHolder.deferredResult.await()
    }
}
