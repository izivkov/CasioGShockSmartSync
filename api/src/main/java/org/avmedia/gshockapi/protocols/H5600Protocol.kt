package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.io.H5600Channel
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.model.StepCounterData

/**
 * Protocol for the DW-H5600 (module 3516 family).
 *
 * Steps and the other LifeLog data come from the LifeLog transfer. All other features use the
 * standard protocol. While a transfer is running, notifications are routed to the transfer by
 * channel; otherwise the standard routing applies.
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600Protocol : StandardProtocol() {
    override fun extractKey(data: String): Int? = if (H5600HealthIO.isActive) H5600HealthIO.channelOf(data).key else super.extractKey(data)

    override suspend fun getStepCount(peek: Boolean): StepCounterData = H5600HealthIO.request(peek)

    override val dataReceivedHandlers: Map<Int, (String) -> Unit>
        get() =
            super.dataReceivedHandlers -
                CasioConstants.CHARACTERISTICS.CASIO_ACTIVITY_RECORD.code +
                mapOf(
                    H5600Channel.DRSP.key to H5600HealthIO::onDrsp,
                    H5600Channel.CONVOY.key to H5600HealthIO::onConvoy,
                    H5600Channel.STATUS.key to H5600HealthIO::onStatus,
                )
}
