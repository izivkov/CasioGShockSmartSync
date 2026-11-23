package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.WatchLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** The end message of the tide transfer comes on the data request characteristic; its first byte is not known, so the characteristic decides. */
class TideChannelTest {
    @Test fun aMessageOnTheDataRequestCharacteristicIsAlwaysDataRequest() {
        for (hex in listOf("0x0A 23", "0x05 23", "0x01 02 03", "0x28 01", "0x23")) {
            val ch = WatchLink.withSource(CasioConstants.CASIO_DATA_REQUEST_CHARACTERISTIC_UUID) { TideTransferIO.channelOf(hex) }
            assertEquals(hex, H5600Channel.DRSP, ch)
        }
    }

    @Test fun aMessageOnTheBulkCharacteristicIsBulk() {
        val ch = WatchLink.withSource(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID) { TideTransferIO.channelOf("0x00 00 00") }
        assertEquals(H5600Channel.CONVOY, ch)
    }

    @Test fun withoutASourceTheFirstByteIsTheFallback() {
        assertEquals(H5600Channel.DRSP, TideTransferIO.channelOf("0x04 23"))
        assertEquals(H5600Channel.CONVOY, TideTransferIO.channelOf("0x05 01 02 03"))
    }
}
