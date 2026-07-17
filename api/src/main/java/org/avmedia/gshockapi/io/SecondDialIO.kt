package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object SecondDialIO {
    fun writeResetSequence(slot: Int = 0) {
        val payload = byteArrayOf(0x21, slot.toByte(), 0x01)
        Connection.write(GetSetMode.SET, payload)
    }
}
