package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object DstWatchStateIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<String>
    }

    suspend fun request(state: IO.DTS_STATE): String {
        return CachedIO.request("1d0${state.state}", ::getDSTWatchState) as String
    }

    private suspend fun getDSTWatchState(key: String): String {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    /*
    There are six clocks on the Casio GW-B5600
    0 is the main clock
    1-5 are the world clocks

    0x1d 00 01 DST0 DST1 TZ0A TZ0B TZ1A TZ1B ff ff ff ff ff
    0x1d 02 03 DST2 DST3 TZ2A TZ2B TZ3A TZ3B ff ff ff ff ff
    0x1d 04 05 DST4 DST5 TZ4A TZ4B TZ5A TZ5B ff ff ff ff ff
    DST: bitwise flags; bit0: DST on, bit1: DST auto
    */

    suspend fun setDST(dstState: String, dst: Int): String {

        val intArray = Utils.toIntArray(dstState)
        intArray[3] = dst

        val newValue = Utils.byteArrayOfIntArray(intArray.toIntArray())
        return Utils.fromByteArrayToHexStrWithSpaces(newValue)
    }

    fun onReceived(data: String) {
        DeferredValueHolder.deferredResult.complete(data)
    }
}