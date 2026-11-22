package org.avmedia.gshockapi.io

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The read-modify-write rule: untouched bytes stay identical, a write is read back, a mismatch is never retried. */
class SafeSettingWriteTest {
    private class FakeWatch(var value: ByteArray, val keeps: Boolean = true, val answers: Boolean = true) {
        val writes = mutableListOf<ByteArray>()
        var reads = 0

        suspend fun read(code: Int): ByteArray? {
            reads++
            return if (answers) value.copyOf() else null
        }

        suspend fun write(frame: ByteArray) {
            writes += frame.copyOf()
            if (keeps) value = frame.copyOf()
        }
    }

    private val original = byteArrayOf(0x4F, 3, 23, 15, 6, 45, 1, 0x7B, 60, 45)

    private fun writer(w: FakeWatch) = SafeSettingWrite(w::read, w::write)

    @Test
    fun writesTheChangeAndReadsItBack() = runBlocking {
        val watch = FakeWatch(original)
        val result = writer(watch).update(0x4F, PhysicalSettingsCodec.BEDTIME_BYTES) { PhysicalSettingsCodec.withBedtime(it, 22, 0) }
        assertEquals(SettingWriteResult.Done, result)
        assertEquals(1, watch.writes.size)
        assertEquals(2, watch.reads)
        // every byte outside the bedtime is the one the watch had
        for (i in original.indices) if (i !in PhysicalSettingsCodec.BEDTIME_BYTES) assertEquals(original[i], watch.value[i])
    }

    @Test
    fun anUnchangedValueIsNotWritten() = runBlocking {
        val watch = FakeWatch(original)
        val result = writer(watch).update(0x4F, PhysicalSettingsCodec.BEDTIME_BYTES) { PhysicalSettingsCodec.withBedtime(it, 23, 15) }
        assertEquals(SettingWriteResult.Unchanged, result)
        assertTrue(watch.writes.isEmpty())
    }

    @Test
    fun aWatchThatDoesNotKeepTheValueIsReportedOnceWithoutARetry() = runBlocking {
        val watch = FakeWatch(original, keeps = false)
        val result = writer(watch).update(0x4F, PhysicalSettingsCodec.BEDTIME_BYTES) { PhysicalSettingsCodec.withBedtime(it, 22, 0) }
        assertTrue(result is SettingWriteResult.Failed)
        assertEquals(1, watch.writes.size)
        assertEquals(2, watch.reads)
        assertArrayEquals(original, watch.value)
    }

    @Test
    fun aChangeThatTouchesAnotherByteIsStoppedBeforeTheWrite() = runBlocking {
        val watch = FakeWatch(original)
        val result = writer(watch).update(0x4F, PhysicalSettingsCodec.BEDTIME_BYTES) { frame ->
            PhysicalSettingsCodec.withBedtime(frame, 22, 0)!!.also { it[8] = 0 }
        }
        assertTrue(result is SettingWriteResult.Failed)
        assertTrue(watch.writes.isEmpty())
    }

    @Test
    fun aSilentWatchIsAPlainErrorAndNothingIsWritten() = runBlocking {
        val watch = FakeWatch(original, answers = false)
        val result = writer(watch).update(0x4F, PhysicalSettingsCodec.BEDTIME_BYTES) { PhysicalSettingsCodec.withBedtime(it, 22, 0) }
        assertTrue(result is SettingWriteResult.Failed)
        assertTrue(watch.writes.isEmpty())
    }

    @Test
    fun aFrameTheCodecDoesNotKnowIsNeverWritten() = runBlocking {
        val watch = FakeWatch(byteArrayOf(0x45, 1, 2))
        val result = writer(watch).update(0x45, ProfileCodec.BYTES) { ProfileCodec.withHeightAndWeight(it, 180, 79) }
        assertTrue(result is SettingWriteResult.Failed)
        assertTrue(watch.writes.isEmpty())
    }

    @Test
    fun anAnswerForAnotherCodeIsNotTrusted() = runBlocking {
        val watch = FakeWatch(byteArrayOf(0x13, 1, 2, 3))
        val result = writer(watch).update(0x4F, PhysicalSettingsCodec.BEDTIME_BYTES) { PhysicalSettingsCodec.withBedtime(it, 22, 0) }
        assertTrue(result is SettingWriteResult.Failed)
        assertTrue(watch.writes.isEmpty())
    }
}
