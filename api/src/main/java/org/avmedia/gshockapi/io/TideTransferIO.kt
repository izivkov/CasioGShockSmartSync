package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.Channel
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.ConnectionKeepAlive

/**
 * Sends a port record to a DW-H5600 through the shared exchange lock. Routing of the watch's answers works as for
 * the workout transfer: while it runs, the H5600 protocol hands bulk and data request notifications to [onDrsp] and [onConvoy].
 */
@RequiresApi(Build.VERSION_CODES.O)
object TideTransferIO {
    @Volatile private var inbox: Channel<WorkoutMsg>? = null

    val isActive: Boolean get() = inbox != null

    /** The channel a message belongs to: the characteristic decides, as for the workouts ([H5600WorkoutIO.channelOf]). */
    fun channelOf(hex: String): H5600Channel = H5600WorkoutIO.channelOf(hex)

    fun onDrsp(hex: String) = push(H5600Channel.DRSP, hex)

    fun onConvoy(hex: String) = push(H5600Channel.CONVOY, hex)

    private fun push(
        channel: H5600Channel,
        hex: String,
    ) {
        if (inbox != null) ConnectionKeepAlive.log("tide rx $channel " + hex.trim().split(' ').take(12).joinToString(" "))
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

    private val link =
        object : WorkoutLink {
            override fun drsp(data: ByteArray) = IO.writeCmd(GetSetMode.DATA_REQUEST, data)

            override fun bulk(data: ByteArray) = IO.writeCmd(GetSetMode.CONVOY, data)

            override suspend fun bulkConfirmed(data: ByteArray): Boolean = IO.writeChecked(GetSetMode.CONVOY, data)
        }
}
