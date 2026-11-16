package org.avmedia.gshockGoogleSync

import org.avmedia.gshockapi.io.H5600LifeLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Replays izivkov's official-app capture btsnoop_hci_DW-H5600-4.log (python gshock_api test_data),
 * for which the Python decoder gives 624 steps, matching the official app.
 */
class H5600LifeLogTest {
    private data class Msg(val tx: Boolean, val handle: Int, val value: ByteArray)

    private val DRSP = 0x10; private val CONVOY = 0x13

    private fun load(name: String = "dw-h5600-lifelog-izivkov-4.btsnoop"): List<Msg> {
        val raw = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()
        val bb = ByteBuffer.wrap(raw)
        var off = 16
        val msgs = mutableListOf<Msg>()
        while (off + 24 <= raw.size) {
            val ilen = bb.getInt(off + 4); val flags = bb.getInt(off + 8)
            off += 24
            val pkt = raw.copyOfRange(off, minOf(off + ilen, raw.size)); off += ilen
            if (pkt.size < 5 || pkt[0] != 0x02.toByte()) continue
            val l2len = (pkt[3].toInt() and 0xFF) or ((pkt[4].toInt() and 0xFF) shl 8)
            val pl = pkt.copyOfRange(5, minOf(5 + l2len, pkt.size))
            if (pl.size < 5 || ((pl[2].toInt() and 0xFF) or ((pl[3].toInt() and 0xFF) shl 8)) != 4) continue
            val att = pl.copyOfRange(4, pl.size)
            if ((att[0].toInt() and 0xFF) !in setOf(0x12, 0x52, 0x1B, 0x1D)) continue
            val handle = (att[1].toInt() and 0xFF) or ((att[2].toInt() and 0xFF) shl 8)
            if (handle != DRSP && handle != CONVOY) continue
            msgs += Msg(flags and 1 == 0, handle, att.copyOfRange(3, att.size))
        }
        return msgs
    }

    private fun isStart(m: Msg) = m.tx && m.handle == DRSP && m.value.size >= 3 &&
        m.value.copyOfRange(0, 3).contentEquals(H5600LifeLog.START.copyOfRange(0, 3))
    private fun isEnd(m: Msg) = !m.tx && m.handle == DRSP && m.value.size >= 2 &&
        m.value[0] == 0x04.toByte() && m.value[1] == 0x2E.toByte()

    /** Same selection as the Python load_hci: the complete attempt with the most bulk data. */
    private fun bestTransfer(msgs: List<Msg>): List<Msg> {
        val starts = msgs.indices.filter { isStart(msgs[it]) }
        return starts.mapIndexed { k, st -> msgs.subList(st, if (k + 1 < starts.size) starts[k + 1] else msgs.size) }
            .filter { seg -> seg.any(::isEnd) }
            .maxBy { seg -> seg.filter { !it.tx && it.handle == CONVOY }.sumOf { it.value.size } }
    }

    @Test fun decodesSameStepsAsOfficialApp() {
        val seg = bestTransfer(load())
        val convoy = seg.filter { !it.tx && it.handle == CONVOY }.map { it.value }
        val announced = linkedMapOf<Int, Int>()
        seg.filter { !it.tx && it.handle == DRSP }.mapNotNull { H5600LifeLog.announcement(it.value) }
            .forEach { (i, len) -> announced.putIfAbsent(i, len) }
        val r = H5600LifeLog.decode(convoy, announced)
        assertNotNull(r)
        val sum = r!!.slots.sumOf { it.steps }
        println("header ${r.headerTimeUtc} UTC, headerSteps=${r.headerSteps}, slots=${r.slots.size}, slotSum=$sum")
        assertEquals(624, sum)

        // Mapping for the Health tab (capture is from Toronto, EDT = UTC-4 on 2026-10-26).
        val data = org.avmedia.gshockGoogleSync.h5600.H5600Steps
            .toStepCounterData(r, java.time.ZoneId.of("America/Toronto"))
        println("local ${data.timestamp}, hourly=${data.hourlySteps}")
        assertEquals(624, data.currentDaySteps)
        assertEquals(624, data.hourlySteps.filterNotNull().sum()) // all of the capture's slots fall on the same local day
        assertEquals(18, data.timestamp!!.hour)
    }

    @Test fun stateMachineReachesEndOnRecordedTraffic() {
        val seg = bestTransfer(load())
        val sync = H5600LifeLog.Sync()
        sync.start()
        // The recorded transfer starts after the setup exchange: jump the machine to "transfer".
        sync.onConvoy(H5600LifeLog.POLL)
        sync.onConvoy(byteArrayOf(0x04, 0x00)); sync.onConvoy(byteArrayOf(0x04, 0x00))
        sync.onConvoy(ByteArray(9).also { it[0] = 0x06 }); sync.onConvoy(ByteArray(9).also { it[0] = 0x06 })
        assertEquals("transfer", sync.phase)
        var acks = 0
        seg.filter { !it.tx }.forEach { m ->
            val w = if (m.handle == CONVOY) sync.onConvoy(m.value) else sync.onDrsp(m.value)
            acks += w.count { it.data.contentEquals(H5600LifeLog.ACK_FRAGMENT) }
        }
        assertTrue(sync.done)
        val fragments = seg.count { !it.tx && it.handle == CONVOY && it.value.isNotEmpty() && it.value[0] == 0x05.toByte() }
        assertEquals(fragments, acks) // every bulk fragment acknowledged
    }

    /** The user's own watch, synced by this app (2026-11-16 20:55); Python decoder gives header 351. */
    @Test fun decodesUsersLiveSync() {
        val seg = bestTransfer(load("dw-h5600-lifelog-user-live1.btsnoop"))
        val convoy = seg.filter { !it.tx && it.handle == CONVOY }.map { it.value }
        val announced = linkedMapOf<Int, Int>()
        seg.filter { !it.tx && it.handle == DRSP }.mapNotNull { H5600LifeLog.announcement(it.value) }
            .forEach { (i, len) -> announced.putIfAbsent(i, len) }
        val r = H5600LifeLog.decode(convoy, announced)!!
        println("live: header ${r.headerTimeUtc} steps=${r.headerSteps} slots=${r.slots.size} sum=${r.slots.sumOf { it.steps }}")
        assertEquals(351, r.headerSteps)
        assertEquals(93, r.slots.sumOf { it.steps }) // same as the Python decoder
    }
}
