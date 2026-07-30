package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

@RequiresApi(Build.VERSION_CODES.O)
object GwBx5600TimeIO {

    private var step: Int = 0
    private var accumulator = ByteArray(0)
    private var result: CompletableDeferred<ByteArray>? = null

    suspend fun set(timeMs: Long? = null) {
        val nowMs = timeMs ?: Clock.systemDefaultZone().millis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())

        Timber.i("GwBx5600TimeIO.set: \$now")

        // Step 1
        Timber.i("Step 1/4: time-slot data")
        var req1 = byteArrayOf(0x05)
        req1 += byteArrayOf(0x1D, 0x00, 0x1D, 0x00) // DST Watch State blocks
        req1 += byteArrayOf(0x24, 0x00, 0x24, 0x01, 0x24, 0x02) // Time Slot blocks

        val notif1 = request(1, req1)
        if (notif1 != null) {
            val wb1Length = 35
            val wb1 = ByteArray(wb1Length) { 0xFF.toByte() }
            System.arraycopy(notif1, 0, wb1, 0, wb1Length.coerceAtMost(notif1.size))
            wb1[0] = 0x02 // command byte: read (0x05) -> write (0x02)
            
            Connection.write(GetSetMode.SP_DATA, wb1)
        }

        // Step 2
        Timber.i("Step 2/4: world-city data")
        var req2 = byteArrayOf(0x03)
        val blocks = ceil(WatchInfo.worldCitiesCount / 2.0).toInt()
        for (i in 0 until blocks) {
            req2 += byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code.toByte(), 0x00)
        }

        val notif2 = request(2, req2)
        if (notif2 != null) {
            val wb2 = notif2.copyOf()
            wb2[0] = 0x06 // command byte: read (0x03) -> write (0x06)
            Connection.write(GetSetMode.SP_DATA, wb2)
        }

        // Step 3
        Timber.i("Step 3/4: city names")
        var req3 = byteArrayOf(0x06)
        for (i in 0 until WatchInfo.worldCitiesCount) {
            val idx = (i / 2) + if (i % 2 != 0) 6 else 0
            req3 += byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code.toByte(), idx.toByte())
        }

        val notif3 = request(3, req3)
        if (notif3 != null) {
            Connection.write(GetSetMode.SP_DATA, notif3)
        }

        // Step 4
        writeTimeCommand(now)
        Timber.i("GwBx5600TimeIO.set: complete")
    }

    private suspend fun request(currentStep: Int, reqPayload: ByteArray): ByteArray? {
        step = currentStep
        accumulator = ByteArray(0)
        result = CompletableDeferred()
        try {
            Connection.write(GetSetMode.SP_REQUEST, reqPayload)
            return withTimeoutOrNull(5000L) {
                result?.await()
            }
        } finally {
            result = null
            accumulator = ByteArray(0)
            step = 0
        }
    }

    private fun writeTimeCommand(now: LocalDateTime) {
        val casioDow = (now.dayOfWeek.value % 7)

        val timeCmd = byteArrayOf(
            0x09,
            (now.year and 0xFF).toByte(),
            ((now.year shr 8) and 0xFF).toByte(),
            now.monthValue.toByte(),
            now.dayOfMonth.toByte(),
            now.hour.toByte(),
            now.minute.toByte(),
            now.second.toByte(),
            casioDow.toByte(),
            0x50,
            0x01
        )
        val hexStr = timeCmd.joinToString("") { "%02X".format(it) }
        Timber.i("Step 4/4: time command: \$hexStr")
        Connection.write(GetSetMode.SET, timeCmd)
    }

    fun onReceived(data: String) {
        if (result == null) return

        // Data comes as hex string from MessageDispatcher (e.g. "0x05 1D ...")
        // We use Utils.toIntArray to safely parse it
        val ints = org.avmedia.gshockapi.utils.Utils.toIntArray(data)
        val bytes = ByteArray(ints.size) { i -> ints[i].toByte() }

        accumulator += bytes

        val expected = when (step) {
            1 -> 101
            2 -> {
                val blocks = ceil(WatchInfo.worldCitiesCount / 2.0).toInt()
                1 + (blocks * 9)
            }
            3 -> 1 + (WatchInfo.worldCitiesCount * 22)
            else -> 0
        }

        val accumulated = accumulator.size

        Timber.d("GwBx5600TimeIO.onReceived: step=\$step accumulated=\${accumulated}B / expected=\${expected}B")

        if (accumulated >= expected) {
            result?.complete(accumulator)
        }
    }
}
