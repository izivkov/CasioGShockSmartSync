package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode

fun strayRawWrite() {
    Connection.write(GetSetMode.SET, byteArrayOf(0x13))
}
