package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.WatchFactory
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.hexToBytes
import org.json.JSONObject

object HandIO {

    fun sendToWatch(cmd:String) {
        // placeholder

        WatchFactory.watch.writeCmd(
            0x000e,
            "1a0412000000".hexToBytes()
        )
        WatchFactory.watch.writeCmd(
            0x000e,
            "1a0418080700".hexToBytes()
        )

        // Adjustment
        // "1a0418->0a<-0700"

        // Minutes
        // 0->15 : 05
        // 15->70 : 07
        // 30->45 : 05
        // 45->0 : 07
        // "1a04180a >07< 00"

        // reset
        // 1a0412000000
        // 1a0418a00500
        // or
        // 1a0418080700  - 9:30 am

        // Counter CW
        // 1a0418a10500
        // 1a0419a00500

        // Counter CW
        // 1a0418090700
        // 1a0400000000

        // Counter CW x5
        // 1a04180d0700
        // 1a0400000000

        // CW
        // 1a04189f0500
        // 1a0419a00500

        // CW x3
        // 1a0418050700
        // 1a0400000000
    }
}