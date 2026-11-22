package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeWriter : WatchWriter {
    val all = ArrayList<List<Int>>()
    val requests = ArrayList<Int>()
    val linkLoss = ArrayList<Int>()
    override fun writeAllFeatures(bytes: ByteArray) { all += bytes.map { it.toInt() and 0xFF } }
    override fun request(code: Int) { requests += code }
    override fun writeLinkLoss(level: Int) { linkLoss += level }
}

class ConnectionKeepAliveTest {
    private val fake = FakeWriter()

    @Before fun install() {
        ConnectionKeepAlive.writer = fake
        ConnectionKeepAlive.clock = { 1_700_000_000_000L }
        ConnectionKeepAlive.clearEvents()
        ConnectionSetup.busy = { false }
        ConnectionSetup.timeWriter = { timeWrites++ }
    }

    private var timeWrites = 0

    @After fun restore() {
        ConnectionKeepAlive.writer = ConnectionWriter
        ConnectionKeepAlive.clock = System::currentTimeMillis
        ConnectionSetup.busy = { org.avmedia.gshockapi.io.H5600HealthIO.isActive }
    }

    private fun receive(data: String) = MessageDispatcher.onReceived(data, H5600Protocol)

    @Test fun aConnectionCheckGetsExactlyOneAnswer() {
        receive("0x49 00")
        assertEquals(listOf(listOf(0x49, 0x01)), fake.all)
        assertTrue(fake.linkLoss.isEmpty())
    }

    @Test fun eachCheckIsAnsweredOnceAndLoggedWithATime() {
        receive("49 00")
        receive("49 00")
        assertEquals(2, fake.all.size)
        val log = ConnectionKeepAlive.recentEvents()
        assertEquals(2, log.size)
        assertTrue(log[0], log[0].startsWith("2023-11-14 ") && log[0].endsWith("49 00 received, 49 01 written"))
    }

    @Test fun anotherCheckValueIsNotAnswered() {
        receive("0x49 05")
        assertTrue(fake.all.isEmpty())
        assertEquals(1, ConnectionKeepAlive.recentEvents().size)
    }

    @Test fun setupValueTwoWritesLinkLossLevelOne() {
        receive("0x47 02")
        assertEquals(listOf(1), fake.linkLoss)
        assertTrue(fake.all.isEmpty())
    }

    @Test fun setupValueOneIsOnlyLogged() {
        receive("0x47 01")
        assertTrue(fake.linkLoss.isEmpty() && fake.all.isEmpty())
        assertTrue(ConnectionKeepAlive.recentEvents().single().endsWith("47 01 received: the watch's setup is done"))
    }

    @Test fun reconnectKindTenRunsTheWholeSetup() {
        ConnectionSetup.reset()
        receive("0x10 00 00 00 00 00 00 00 0A 00")
        assertEquals(listOf(0x11), fake.requests)
        receive("0x11 01 02 03 04 0F 06 07 08 09 0A 0B 0C")
        assertEquals(listOf(0x11, 0x01, 0x02, 0x03, 0x04, 0x0B, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C), fake.all[0])
        assertEquals(listOf(0x11, 0x3B), fake.requests)
        receive("0x3B 01 02 03")
        assertEquals(listOf(0x3B, 0x01, 0x02, 0x03), fake.all[1])
        assertEquals(listOf(0x11, 0x3B, 0x3A), fake.requests)
        receive("0x3A 00 01 02 03 04 05 06 07 08 09 10 11")
        assertEquals(listOf(0x3A, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0xDC, 0x05, 0x09, 0x10, 0x11), fake.all[2])
        finishWithName()
    }

    private fun finishWithName() {
        assertEquals(0x23, fake.requests.last())
        receive("0x23 41 42 43")
        assertEquals(listOf(0x23, 0x41, 0x42, 0x43), fake.all.last())
        assertEquals(0x28, fake.requests.last())
        assertEquals("the time write runs on every connection", 1, timeWrites)
    }

    @Test fun kindFourteenAlsoRunsNameConditionAndTimeWrite() {
        ConnectionSetup.reset()
        receive("0x10 00 00 00 00 00 00 00 0E 00")
        receive("0x11 01 02 03 04 04 06 07 08")
        finishWithName()
        assertTrue(fake.requests.none { it == 0x3A || it == 0x3B })
    }

    @Test fun otherKindsSkipThe3AAnd3BSteps() {
        ConnectionSetup.reset()
        receive("0x10 00 00 00 00 00 00 00 01 00")
        receive("0x11 01 02 03 04 04 06 07 08")
        assertEquals(1, fake.all.size)
        assertEquals(listOf(0x11, 0x23), fake.requests)
        assertEquals(0x00, fake.all[0][5])
    }

    @Test fun noSetupRunsWhileATransferIsActiveOrAfterItIsDone() {
        ConnectionSetup.reset()
        ConnectionSetup.busy = { true }
        receive("0x10 00 00 00 00 00 00 00 0A 00")
        assertTrue(fake.requests.isEmpty())
        ConnectionSetup.busy = { false }
        ConnectionSetup.reset()
        receive("0x10 00 00 00 00 00 00 00 01 00")
        receive("0x11 01 02 03 04 04 06 07 08")
        receive("0x23 41")
        receive("0x11 01 02 03 04 04 06 07 08")
        assertEquals("a finished setup does not answer a second 0x11", 2, fake.all.size)
    }
}
