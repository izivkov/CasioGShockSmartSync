package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.casio.NotificationGate
import org.avmedia.gshockapi.io.H5600Channel
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.io.H5600WorkoutIO
import org.avmedia.gshockapi.io.TideTransferIO
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
    /**
     * Codes the watch sends on its own and that are handled at any time, also in the middle of a transfer: the connection check `49`,
     * the setup notice `47`, the time manager `39` and the notification manager `3D`.
     */
    val UNSOLICITED_ALWAYS: Set<Int> = setOf(ConnectionKeepAlive.CHECK, ConnectionKeepAlive.SETUP, 0x39, 0x3D)

    override fun extractKey(data: String): Int? {
        // The watch's own connection check and setup notices are answered even in the middle of a transfer.
        val first = super.extractKey(data)
        if (first != null && first in UNSOLICITED_ALWAYS) return first
        if (TideTransferIO.isActive) return TideTransferIO.channelOf(data).key
        if (H5600WorkoutIO.isActive) return H5600WorkoutIO.channelOf(data).key
        return if (H5600HealthIO.isActive) H5600HealthIO.channelOf(data).key else first
    }

    override suspend fun getStepCount(peek: Boolean): StepCounterData = H5600HealthIO.request(peek)

    override val dataReceivedHandlers: Map<Int, (String) -> Unit>
        get() = withSetup(
            super.dataReceivedHandlers -
                CasioConstants.CHARACTERISTICS.CASIO_ACTIVITY_RECORD.code +
                mapOf(
                    ConnectionKeepAlive.CHECK to ConnectionKeepAlive::onCheck,
                    ConnectionKeepAlive.SETUP to ConnectionKeepAlive::onSetup,
                    0x3A to ConnectionSetup::on3a,
                    0x3B to ConnectionSetup::on3b,
                    H5600Channel.DRSP.key to { d: String -> if (TideTransferIO.isActive) TideTransferIO.onDrsp(d) else if (H5600WorkoutIO.isActive) H5600WorkoutIO.onDrsp(d) else H5600HealthIO.onDrsp(d) },
                    H5600Channel.CONVOY.key to { d: String -> if (TideTransferIO.isActive) TideTransferIO.onConvoy(d) else if (H5600WorkoutIO.isActive) H5600WorkoutIO.onConvoy(d) else H5600HealthIO.onConvoy(d) },
                    H5600Channel.STATUS.key to H5600HealthIO::onStatus,
                ),
        )

    /** The library's own handlers for 0x10 and 0x11 still run; the connection setup watches the same frames. */
    private fun withSetup(handlers: Map<Int, (String) -> Unit>): Map<Int, (String) -> Unit> {
        val kind = handlers[CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code]
        val ble = handlers[CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code]
        val condition = handlers[CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code]
        val name = handlers[CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code]
        return handlers + mapOf<Int, (String) -> Unit>(
            CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code to { d -> condition?.invoke(d); ConnectionSetup.on28(d); NotificationGate.onCondition(d) },
            CasioConstants.CHARACTERISTICS.CASIO_CURRENT_TIME_MANAGER.code to ConnectionSetup::on39,
            CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code to { d -> name?.invoke(d); ConnectionSetup.on23(d) },
            CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code to { d -> kind?.invoke(d); ConnectionSetup.onKind(d) },
            CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code to { d -> ble?.invoke(d); ConnectionSetup.onBle(d) },
        )
    }
}
