package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.io.GwBx5600TimeIO

@RequiresApi(Build.VERSION_CODES.O)
object MipProtocol : StandardProtocol() {
    override suspend fun setTime(timeMs: Long?, offset: Long?) {
        GwBx5600TimeIO.set(timeMs)
    }
}
