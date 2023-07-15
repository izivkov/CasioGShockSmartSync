package org.avmedia.gshockapi.io

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.BleScannerLocal
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.DeviceCharacteristics
import org.avmedia.gshockapi.utils.WatchDataListener
import timber.log.Timber

object WaitForConnectionIO {

    suspend fun request(
        context: Context,
        bleScannerLocal: BleScannerLocal,
        deviceId: String? = "",
        deviceName: String? = ""
    ): String {
        return waitForConnection(context, bleScannerLocal, deviceId, deviceName)
    }

    private suspend fun waitForConnection(
        context: Context,
        bleScannerLocal: BleScannerLocal,
        deviceId: String? = "",
        deviceName: String? = ""
    ): String {

        if (Connection.isConnected() || Connection.isConnecting()) {
            return "Connecting"
        }

        Connection.init(context)
        WatchDataListener.init()

        // TODO: remove  bleScannerLocal = BleScannerLocal(context)
        bleScannerLocal.startConnection(deviceId, deviceName)

        val deferredResult = CompletableDeferred<String>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                "waitForConnection", deferredResult as CompletableDeferred<Any>
            )
        )

        fun waitForConnectionSetupComplete() {
            ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
                when (it) {
                    ProgressEvents["ConnectionSetupComplete"] -> {
                        val device =
                            ProgressEvents.getPayload("ConnectionSetupComplete") as BluetoothDevice
                        DeviceCharacteristics.init(device)

                        CachedIO.clearCache()
                        CachedIO.resultQueue.dequeue("waitForConnection")?.complete("OK")
                    }
                }
            }, { throwable ->
                Timber.d("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
        }

        waitForConnectionSetupComplete()

        return deferredResult.await()
    }
}