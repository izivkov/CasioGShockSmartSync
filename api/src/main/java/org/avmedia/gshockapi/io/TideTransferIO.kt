package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.Channel
import org.avmedia.gshockapi.ble.GetSetMode

/**
 * Sends a port record to a DW-H5600 through the shared exchange lock (notes I5). Routing of the watch's answers works as for
 * the workout transfer: while it runs, the H5600 protocol hands bulk and data request notifications to [onDrsp] and [onConvoy].
 */
@RequiresApi(Build.VERSION_CODES.O)
object TideTransferIO {
    @Volatile private var inbox: Channel<WorkoutMsg>? = null

    val isActive: Boolean get() = inbox != null

    fun channelOf(hex: String): H5600Channel = H5600WorkoutIO.channelOf(hex)

    fun onDrsp(hex: String) = push(H5600Channel.DRSP, hex)

    fun onConvoy(hex: String) = push(H5600Channel.CONVOY, hex)

    private fun push(channel: H5600Channel, hex: String) {
        inbox?.trySend(WorkoutMsg(channel, H5600HealthIO.bytesOf(hex)))
    }

    /** Waits for the exchange lock, then sends [record]. Returns an error text or null when the watch took it. */
    suspend fun send(record: ByteArray): String? {
        WatchExchange.mutex.lock()
        val channel = Channel<WorkoutMsg>(Channel.UNLIMITED)
        try {
            inbox = channel
            return TideTransfer(link, channel).run(record).error
        } finally {
            inbox = null
            WatchExchange.mutex.unlock()
        }
    }

    private val link = object : WorkoutLink {
        override fun drsp(data: ByteArray) = IO.writeCmd(GetSetMode.DATA_REQUEST, data)

        override fun bulk(data: ByteArray) = IO.writeCmd(GetSetMode.CONVOY, data)
    }
}
