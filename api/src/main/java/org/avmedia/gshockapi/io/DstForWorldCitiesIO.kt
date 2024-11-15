package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object DstForWorldCitiesIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<String>
    }

    suspend fun request(cityNumber: Int): String {
        return CachedIO.request("1e0$cityNumber", ::getDSTForWorldCities) as String
    }

    private suspend fun getDSTForWorldCities(key: String): String {

        DeferredValueHolder.deferredResult = CompletableDeferred<String>()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
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

    suspend fun setDST(dst: String, casioTimeZone: CasioTimeZoneHelper.CasioTimeZone): String {
        val intArray = Utils.toIntArray(dst)
        if (intArray.size == 7) {
            intArray[4] = casioTimeZone.offset
            intArray[5] = casioTimeZone.dstOffset.toInt()
            intArray[6] = casioTimeZone.dstRules
        }

        val dstByteArray = Utils.byteArrayOfIntArray(intArray.toIntArray())
        return Utils.fromByteArrayToHexStrWithSpaces(dstByteArray)
    }

    fun onReceived(data: String) {
        DeferredValueHolder.deferredResult.complete(data)
    }
}