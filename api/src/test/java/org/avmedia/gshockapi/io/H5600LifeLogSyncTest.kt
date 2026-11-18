package org.avmedia.gshockapi.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H5600LifeLogSyncTest {

    @Test
    fun endAnnouncementFinishesTransferWithoutWritingBack() {
        val sync = H5600LifeLogSync()
        sync.start()

        // Transition through setup to TRANSFER phase
        sync.onMessage(H5600Channel.CONVOY, byteArrayOf(0, 0, 0))
        sync.onMessage(H5600Channel.CONVOY, byteArrayOf(4, 0))
        sync.onMessage(H5600Channel.CONVOY, byteArrayOf(4, 0))
        sync.onMessage(H5600Channel.CONVOY, ByteArray(9) { 0x06.toByte() })
        sync.onMessage(H5600Channel.CONVOY, ByteArray(9) { 0x06.toByte() })

        assertFalse(sync.isFinished)

        // Receive END announcement (04 2E ...)
        val endBytes = byteArrayOf(0x04, 0x2E, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val writes = sync.onMessage(H5600Channel.DRSP, endBytes)

        assertTrue(sync.isFinished)
        assertTrue(writes.isEmpty())
    }
}
