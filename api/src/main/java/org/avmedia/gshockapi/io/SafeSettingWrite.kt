package org.avmedia.gshockapi.io

/** How a checked write ended. [Failed.message] is plain text for the person using the app. */
sealed class SettingWriteResult {
    data object Unchanged : SettingWriteResult()

    data object Done : SettingWriteResult()

    data class Failed(val message: String) : SettingWriteResult()
}

/**
 * Read, change, write, read back. Used for every write to a watch setting, because the watch is the only
 * place the value lives:
 * 1. read the current frame of request [code];
 * 2. the caller changes only the decoded fields, and says which frame bytes it may touch ([touched]);
 * 3. any other byte that differs stops the write;
 * 4. write, wait [settle], read again, and compare the whole frame with what was written.
 * A mismatch is reported once and never retried. No code here knows a layout.
 */
class SafeSettingWrite(
    private val read: suspend (code: Int) -> ByteArray?,
    private val write: suspend (frame: ByteArray) -> Unit,
    private val settle: suspend () -> Unit = {},
) {
    suspend fun update(
        code: Int,
        touched: Set<Int>,
        expectedBack: (before: ByteArray, written: ByteArray) -> ByteArray = { _, written -> written },
        change: (ByteArray) -> ByteArray?,
    ): SettingWriteResult = updateWith(code, { read(code) }, touched, expectedBack, change)

    /**
     * Same as [update] for a value that is read with its own request ([readValue]), such as the per-activity
     * items. [expectedBack] says what the watch should return after the write; it differs from the written frame
     * only where the watch rewrites a command byte (0x3D).
     */
    suspend fun updateWith(
        code: Int,
        readValue: suspend () -> ByteArray?,
        touched: Set<Int>,
        expectedBack: (before: ByteArray, written: ByteArray) -> ByteArray = { _, written -> written },
        change: (ByteArray) -> ByteArray?,
    ): SettingWriteResult {
        val before = readValue()
        if (before == null || before.isEmpty() || before[0] != code.toByte()) {
            return SettingWriteResult.Failed("The watch did not answer. Nothing was changed.")
        }
        val after = change(before) ?: return SettingWriteResult.Failed("This setting is not in the layout the app knows. Nothing was changed.")
        if (after.size != before.size || before.indices.any { it !in touched && before[it] != after[it] }) {
            return SettingWriteResult.Failed("The change would alter other bytes. Nothing was changed.")
        }
        if (after.contentEquals(before)) return SettingWriteResult.Unchanged
        write(after)
        settle()
        val back = readValue() ?: return SettingWriteResult.Failed("The watch did not answer after the change. Check the setting on the watch.")
        return if (back.contentEquals(expectedBack(before, after))) {
            SettingWriteResult.Done
        } else {
            SettingWriteResult.Failed("The watch did not keep the change. Check the setting on the watch.")
        }
    }
}
