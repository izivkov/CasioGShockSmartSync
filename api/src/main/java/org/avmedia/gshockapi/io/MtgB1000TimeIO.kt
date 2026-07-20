package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import timber.log.Timber

/**
 * MTG-B1000 dual-dial time-set implementation.
 *
 * The MTG-B1000 is identical to the standard G-Shock time protocol for the
 * main dial, plus a second-dial sequence bracketed by ResetSequence commands.
 *
 * Protocol confirmed from btsnoop_hci_mgt_b1000.log:
 *
 *  ── Main dial ───────────────────────────────────────────────────────────
 *  Identical to standard watches — handled entirely by existing IO classes
 *  via the normal SET_TIME dispatch path.
 *
 *  ── Second dial ─────────────────────────────────────────────────────────
 *  [1758] WRITE 210001        ResetSequence start (dial=0)
 *  [1763] READ  0x1d          DstWatchStateIO.request(state=ZERO)
 *  [1766] WRITE 0x1d          DstWatchStateIO.send_to_watch()
 *  [1769] READ  0x1e city 0   DstForWorldCitiesIO.request(city_number=0)
 *  [1774] READ  0x1e city 1   DstForWorldCitiesIO.request(city_number=1)
 *  [1777] WRITE 0x1e city 0   DstForWorldCitiesIO echo write-back
 *  [1780] WRITE 0x1e city 1   DstForWorldCitiesIO echo write-back
 *  [1783] READ  0x1f city 0   WorldCitiesIO.request(city_number=0)
 *  [1787] READ  0x1f city 1   WorldCitiesIO.request(city_number=1)
 *  [1783] WRITE 0x24 city 0   WorldCitiesIO echo write-back
 *  [1787] WRITE 0x24 city 1   WorldCitiesIO echo write-back
 *  [1793] WRITE 210101        ResetSequence end (dial=1)
 *
 * ResetSequence byte format: 21 {dial_index} 01
 */

object MtgB1000TimeIO {

    // ResetSequence commands confirmed from log [1758] and [1793]
    private val RESET_SEQUENCE_START = byteArrayOf(0x21, 0x00, 0x01) // dial 0
    private val RESET_SEQUENCE_END = byteArrayOf(0x21, 0x01, 0x01)   // dial 1

    /**
     * Run the second-dial sequence after the main time has been set.
     *
     * Call this immediately after the standard SET_TIME command completes.
     * Reads current DST, city, and world-city data from the watch, then
     * writes them back bracketed by ResetSequence commands so the second
     * analogue dial syncs to the second world city.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun setSecondDial() {
        try {
            Timber.i("MtgB1000TimeIO: starting second dial sequence")

            // ResetSequence start
            IO.writeCmd(GetSetMode.SET, RESET_SEQUENCE_START)
            Timber.i("ResetSequence start (210001)")

            // Read and write back DST watch state (0x1d)
            val dstData = DstWatchStateIO.request(IO.DstState.ZERO)
            val dstBytes = IOFunctional.toCasioCmd(dstData)
            IO.writeCmd(GetSetMode.SET, dstBytes)
            Timber.i("DST watch state written back")

            // Read and write back DST city settings (0x1e) for both cities
            val dstCity0 = DstForWorldCitiesIO.request(cityNumber = 0)
            val dstCity1 = DstForWorldCitiesIO.request(cityNumber = 1)
            val dstCity0Bytes = IOFunctional.toCasioCmd(dstCity0)
            val dstCity1Bytes = IOFunctional.toCasioCmd(dstCity1)
            IO.writeCmd(GetSetMode.SET, dstCity0Bytes)
            IO.writeCmd(GetSetMode.SET, dstCity1Bytes)
            Timber.i("DST city data written back")

            // Read and write back world city coordinates (0x1f) for both cities
            val wc0 = WorldCitiesIO.request(cityNumber = 0)
            val wc1 = WorldCitiesIO.request(cityNumber = 1)
            val wc0Bytes = IOFunctional.toCasioCmd(wc0)
            val wc1Bytes = IOFunctional.toCasioCmd(wc1)
            IO.writeCmd(GetSetMode.SET, wc0Bytes)
            IO.writeCmd(GetSetMode.SET, wc1Bytes)
            Timber.i("World city data written back")

            // ResetSequence end
            IO.writeCmd(GetSetMode.SET, RESET_SEQUENCE_END)
            Timber.i("ResetSequence end (210101)")

            Timber.i("MtgB1000TimeIO: second dial sequence complete")
        } catch (e: Exception) {
            Timber.e(e, "MtgB1000TimeIO: error during second dial sequence")
            throw e
        }
    }
}
