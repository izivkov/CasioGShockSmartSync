package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIO {

    suspend fun request(): String {
        val homeCityRaw = WorldCitiesIO.request(0)
        return Utils.toAsciiString(homeCityRaw, 2)
    }
}