package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object DstForWorldCitiesIO {

    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(cityNumber: Int): String =
        CachedIO.request("1e0$cityNumber") { key ->
            getDSTForWorldCities(key)
        }

    private suspend fun getDSTForWorldCities(key: String): String {

        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: ""
    }

    /*
    0x1e 0-5 TZ_A TZ_B TZ_OFF TZ_DSTOFF TZ_DSTRULES
    A/B seem to be ignored by the watch
    OFF & DSTOFF in 15 minute intervals

    Timezones selectable on the watch:
                       A  B   OFF DSTOFF DSTRULES
    BAKER ISLAND       39 01  D0  04     00
    PAGO PAGO          D7 00  D4  04     00
    HONOLULU           7B 00  D8  04     00
    ...
     */

    fun setDST(dst: String, casioTimeZone: CasioTimeZoneHelper.CasioTimeZone): String =
        Utils.toIntArray(dst)
            .takeIf { it.size == 7 }
            ?.apply {
                this[4] = casioTimeZone.offset
                this[5] = casioTimeZone.dstOffset.toInt()
                this[6] = casioTimeZone.dstRules
            }
            ?.let { intArray ->
                Utils.byteArrayOfIntArray(intArray.toIntArray())
                    .let(Utils::fromByteArrayToHexStrWithSpaces)
            }
            ?: dst


    fun onReceived(data: String) {
        state.deferredResult?.complete(data)

        // Do not reset state here, as it is used in the request function.
        // state = State()
    }
}
