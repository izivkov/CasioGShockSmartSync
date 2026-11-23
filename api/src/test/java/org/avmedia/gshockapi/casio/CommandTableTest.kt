package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.ble.GetSetMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Walks the command table and checks every entry against the exchange rules (docs/exchange-review.md), written out here on their own. */
class CommandTableTest {
    @Before fun strict() { CommandTable.strict = true }

    @After fun loose() { CommandTable.strict = false }

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun send(handle: GetSetMode, vararg v: Int): Verdict = CommandTable.check(handle, b(*v))

    @Test fun everyRequestTimesOutAfterTenSecondsAndEveryEntryIsUnique() {
        assertEquals(10_000L, CommandTable.REQUEST_TIMEOUT_MS)
        assertEquals(20, CommandTable.FRAME_CUT)
        assertTrue(CommandTable.specs.all { it.timeoutMs == 10_000L })
        assertEquals(CommandTable.specs.size, CommandTable.specs.map { it.code }.toSet().size)
        assertTrue(CommandTable.specs.all { it.read != null || it.write != null || it.unsolicited })
    }

    @Test fun writeTypesFollowTheRules() {
        val withResponse = setOf(WatchChannel.ALL_FEATURES, WatchChannel.DATA_REQUEST, WatchChannel.SP_DATA)
        for (c in WatchChannel.values()) assertEquals(c.name, c in withResponse, c.withResponse)
        assertEquals(WatchChannel.READ_REQUEST, WatchChannel.of(GetSetMode.GET))
        assertEquals(WatchChannel.NOTIFICATION, WatchChannel.of(GetSetMode.NOTIFY))
        assertTrue(CommandTable.specs.all { it.write == null || it.write.channel == WatchChannel.ALL_FEATURES })
    }

    @Test fun writeOnlyAndNotificationOnlyCodes() {
        for (code in listOf(0x09, 0x25, 0x49)) {
            val s = CommandTable.spec(code)!!
            assertNull("0x%02X is never read".format(code), s.read)
            assertNotNull(s.write)
            assertTrue(CommandTable.check(GetSetMode.GET, b(code)) is Verdict.Reject)
        }
        for (code in listOf(0x47, 0x0A)) {
            val s = CommandTable.spec(code)!!
            assertNull(s.read)
            assertNull(s.write)
            assertTrue(s.unsolicited)
            assertTrue(CommandTable.check(GetSetMode.SET, b(code, 1)) is Verdict.Reject)
        }
    }

    @Test fun theCodesTheWatchSendsUnaskedAreMarked() {
        for (code in listOf(0x28, 0x39, 0x3D, 0x47, 0x49)) assertTrue("0x%02X".format(code), CommandTable.spec(code)!!.unsolicited)
    }

    @Test fun optionBytesAreRequiredWhereTheRuleSaysSo() {
        for (code in listOf(0x1F, 0x24, 0x2E, 0x30, 0x31, 0x44, 0x4E, 0x56, 0x58, 0x54)) {
            assertTrue("bare 0x%02X is never sent".format(code), send(GetSetMode.GET, code) is Verdict.Reject)
        }
        assertTrue(send(GetSetMode.GET, 0x1F, 0) is Verdict.Send)
        assertTrue(send(GetSetMode.GET, 0x2E, 2) is Verdict.Send)
        assertTrue(send(GetSetMode.GET, 0x54, 0, 3, 0x46, 0) is Verdict.Send)
        for (code in listOf(0x10, 0x11, 0x13, 0x28, 0x42, 0x45, 0x4F)) {
            assertTrue("0x%02X is read bare".format(code), send(GetSetMode.GET, code) is Verdict.Send)
            assertTrue("0x%02X takes no option byte".format(code), send(GetSetMode.GET, code, 0) is Verdict.Reject)
        }
    }

    @Test fun theNotificationManagerIsAlwaysReadAs3D30() {
        assertTrue(send(GetSetMode.GET, 0x3D) is Verdict.Reject)
        assertTrue(send(GetSetMode.GET, 0x3D, 0x31) is Verdict.Reject)
        assertTrue(send(GetSetMode.GET, 0x3D, 0x30) is Verdict.Send)
    }

    @Test fun anAllFeaturesWriteIsCutToTwentyBytesAndOtherFramesAreNot() {
        val long = ByteArray(25) { (it + 1).toByte() }.also { it[0] = 0x53 }
        val cut = (CommandTable.check(GetSetMode.SET, long) as Verdict.Send).frame
        assertEquals(20, cut.size)
        assertEquals(long.take(20), cut.toList())
        assertEquals(25, (CommandTable.check(GetSetMode.CONVOY, ByteArray(25)) as Verdict.Send).frame.size)
        assertEquals(25, (CommandTable.check(GetSetMode.NOTIFY, ByteArray(25)) as Verdict.Send).frame.size)
        assertEquals(20, CommandTable.cut(ByteArray(40)).size)
        assertEquals(5, CommandTable.cut(ByteArray(5)).size)
    }

    @Test fun aCodeThatIsNotInTheTableIsRefusedInTests() {
        assertTrue(send(GetSetMode.SET, 0x7E, 1) is Verdict.Reject)
        CommandTable.strict = false
        assertTrue(send(GetSetMode.SET, 0x7E, 1) is Verdict.Send)
    }

    @Test fun longProcessesAndTheirLimits() {
        for (code in listOf(0x09, 0x25, 0x1D, 0x1E, 0x1F, 0x39)) assertEquals("0x%02X".format(code), LongProcess.TIME_EXCHANGE, CommandTable.spec(code)!!.process)
        assertEquals(LongProcess.UPDATE_CHECK, CommandTable.spec(0x20)!!.process)
        assertEquals(30_000L, LongProcess.TIME_EXCHANGE.totalMs)
        assertEquals(20_000L, LongProcess.SETUP_STEP.totalMs)
        assertEquals(5_000L, LongProcess.LIFELOG.answerMs)
        assertEquals(10_000L, LongProcess.LIFELOG.writeResultMs)
        assertEquals(listOf(5_000L, 10_000L, 30_000L), listOf(LongProcess.WORKOUT.answerMs, LongProcess.WORKOUT.writeResultMs, LongProcess.WORKOUT.totalMs))
        assertEquals(400L, LongProcess.TIDE.chunkGapMs)
        assertEquals(70_000L, LongProcess.TIDE.totalMs)
        assertEquals(5_000L, LongProcess.TIDE.answerMs)
        assertEquals(2_000L, LongProcess.NOTIFICATION_TEXTS.chunkGapMs)
    }

    @Test fun shortestAnswersFollowTheRules() {
        val rules = mapOf(
            0x10 to 8, 0x11 to 11, 0x13 to 16, 0x15 to 4, 0x16 to 16, 0x18 to 6, 0x22 to 11, 0x28 to 9, 0x2E to 6, 0x3A to 9,
            0x3D to 16, 0x41 to 4, 0x42 to 5, 0x43 to 16, 0x45 to 9, 0x4F to 9, 0x50 to 4, 0x51 to 5, 0x53 to 14, 0x55 to 14, 0x57 to 2,
        )
        for ((code, min) in rules) assertEquals("0x%02X".format(code), min, CommandTable.minValue(code))
    }

    @Test fun anAnswerIsMatchedByItsFirstBytes() {
        assertTrue(CommandTable.matches(b(0x28), b(0x28, 1, 2)))
        assertFalse(CommandTable.matches(b(0x28), b(0x29, 1, 2)))
        assertTrue(CommandTable.matches(b(0x1F, 1), b(0x1F, 1, 9)))
        assertFalse(CommandTable.matches(b(0x1F, 1), b(0x1F, 0, 9)))
        assertTrue(CommandTable.matches(b(0x3D, 0x30), b(0x3D, 0x30, 0, 2)))
        // 0x56 answers `56` + value without echoing the activity (log 2026-11-23: `56 02` got `56 6D 03 20`)
        assertTrue(CommandTable.matches(b(0x56, 2), b(0x56, 0x6D, 3, 0x20)))
        assertTrue(CommandTable.longEnough(b(0x56, 2), b(0x56, 0x6D, 3, 0x20)))
        assertTrue(CommandTable.matches(b(0x54, 0, 3, 0x46, 0), b(0x54, 3, 0x46, 1)))
        assertFalse(CommandTable.longEnough(b(0x28), b(0x28, 1)))
        assertTrue(CommandTable.longEnough(b(0x42), b(0x42, 0x0A, 0xFF, 0x7F, 0xFF, 0x7F)))
    }

    @Test fun theSettingsScreensCodesAreReadBackAfterTheWriteResult() {
        for (code in listOf(0x13, 0x2E, 0x3D, 0x40, 0x41, 0x42, 0x43, 0x45, 0x4E, 0x4F, 0x51, 0x53, 0x54, 0x57)) {
            assertEquals("0x%02X".format(code), ReadBack.AFTER_WRITE_RESULT, CommandTable.spec(code)!!.readBack)
        }
        for (code in listOf(0x09, 0x22, 0x23, 0x25, 0x39, 0x49, 0x11, 0x3A)) assertEquals(ReadBack.NONE, CommandTable.spec(code)!!.readBack)
    }
}
