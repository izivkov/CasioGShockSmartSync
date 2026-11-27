package org.avmedia.gshockapi.io

/**
 * Notification sounds (0x42). Index 0: bits 0-1 sound mode, bits 2-3 vibration mode (0 off, 1 user custom, 2 on).
 * Index 1, 2: sound mask per event; 3, 4: vibration mask per event (16 bits, low byte first, bit = event number,
 * used in mode 1). Index 5: sound pattern 0 mute, 1 A, 2 B, 3 C. Every other bit and byte stays as read.
 */
object NotificationSoundCodec {
    const val CODE = 0x42

    /** The GBX-H5600 answers 5 value bytes (index 0 to 4): there is no sound pattern byte, so [Setting.pattern] is null. */
    private const val MIN_INDEX = 4
    const val CUSTOM = 1

    val EVENTS: List<String> =
        listOf(
            "Call", "Other notifications", "Training, form and zone alerts", "Interval timer", "Step reminder",
            "Goal reached", "Auto lap", "Auto pause", "Breathing, SpO2 and stress done", "Timer and alarm",
            "Barometer", "GPS fix", "Low battery", "Log full", "MS alert (meaning not confirmed)",
        )
    val MODES: List<String> = listOf("Off", "Custom (per event)", "On")
    val PATTERNS: List<String> = listOf("Mute", "Pattern A", "Pattern B", "Pattern C")

    data class Setting(val soundMode: Int, val vibrationMode: Int, val soundMask: Int, val vibrationMask: Int, val pattern: Int?) {
        fun soundOn(event: Int): Boolean = soundMask shr event and 1 != 0

        fun vibrationOn(event: Int): Boolean = vibrationMask shr event and 1 != 0
    }

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, MIN_INDEX)) return null
        val flags = Frame.u8(frame, 0) ?: return null
        val sound = flags and 3
        val vibration = flags shr 2 and 3
        val pattern = Frame.u8(frame, 5)
        if (sound > 2 || vibration > 2 || (pattern != null && pattern > 3)) return null
        val soundMask = (Frame.u8(frame, 1) ?: return null) or ((Frame.u8(frame, 2) ?: return null) shl 8)
        val vibrationMask = (Frame.u8(frame, 3) ?: return null) or ((Frame.u8(frame, 4) ?: return null) shl 8)
        return Setting(sound, vibration, soundMask, vibrationMask, pattern)
    }

    fun withSoundMode(
        frame: ByteArray,
        mode: Int,
    ): ByteArray? = withMode(frame, mode, 0)

    fun withVibrationMode(
        frame: ByteArray,
        mode: Int,
    ): ByteArray? = withMode(frame, mode, 2)

    private fun withMode(
        frame: ByteArray,
        mode: Int,
        shift: Int,
    ): ByteArray? {
        if (parse(frame) == null || mode !in 0..2) return null
        val flags = Frame.u8(frame, 0) ?: return null
        return Frame.withBytes(frame, mapOf(0 to ((flags and (3 shl shift).inv()) or (mode shl shift))))
    }

    fun withSoundEvent(
        frame: ByteArray,
        event: Int,
        on: Boolean,
    ): ByteArray? = withEvent(frame, event, on, 1)

    fun withVibrationEvent(
        frame: ByteArray,
        event: Int,
        on: Boolean,
    ): ByteArray? = withEvent(frame, event, on, 3)

    private fun withEvent(
        frame: ByteArray,
        event: Int,
        on: Boolean,
        index: Int,
    ): ByteArray? {
        if (parse(frame) == null || event !in EVENTS.indices) return null
        val low = Frame.u8(frame, index) ?: return null
        val high = Frame.u8(frame, index + 1) ?: return null
        val mask = Frame.bits(low or (high shl 8), 1 shl event, on)
        return Frame.withBytes(frame, mapOf(index to (mask and 0xFF), index + 1 to (mask shr 8 and 0xFF)))
    }

    fun withPattern(
        frame: ByteArray,
        pattern: Int,
    ): ByteArray? {
        if (parse(frame)?.pattern == null || pattern !in PATTERNS.indices) return null
        return Frame.withBytes(frame, mapOf(5 to pattern))
    }

    val MODE_BYTES: Set<Int> = setOf(1)
    val SOUND_MASK_BYTES: Set<Int> = setOf(2, 3)
    val VIBRATION_MASK_BYTES: Set<Int> = setOf(4, 5)
    val PATTERN_BYTES: Set<Int> = setOf(6)
}

/**
 * Notification manager (0x3D). Index 0: command (a read returns 0x30). Index 1 bits 0-1: language 1; index 2-5:
 * second language priorities; index 6 bits 1-2: off at night; index 16 bit 0: pop-up size. The app writes it only when
 * one of its own fields changes: the whole value back with index 0 = 0x31 (language) or 0x32 (off at night). A change
 * to the value the watch already has returns the frame unchanged, so nothing is written. The values' meanings are
 * not confirmed: they are shown as numbers.
 */
object NotificationManagerCodec {
    const val CODE = 0x3D

    /** Index 6 (off at night) is the last one needed; index 16 (pop-up size) is read when the answer has it (the GBX-H5600 sends 16 bytes after the 30, so it is there). */
    private const val MIN_INDEX = 6
    const val READ_COMMAND = 0x30
    const val LANGUAGE_CHANGED = 0x31
    const val NIGHT_CHANGED = 0x32

    data class Setting(val language: Int, val offAtNight: Int, val popUpSize: Int?)

    fun parse(frame: ByteArray): Setting? {
        if (!Frame.hasIndex(frame, CODE, MIN_INDEX)) return null
        val lang = (Frame.u8(frame, 1) ?: return null) and 3
        val night = ((Frame.u8(frame, 6) ?: return null) shr 1) and 3
        val popUp = Frame.u8(frame, 16)?.and(1)
        return Setting(lang, night, popUp)
    }

    fun withLanguage(
        frame: ByteArray,
        language: Int,
    ): ByteArray? {
        val current = parse(frame) ?: return null
        if (language !in 0..3) return null
        if (language == current.language) return frame.copyOf()
        val flags = Frame.u8(frame, 1) ?: return null
        return Frame.withBytes(frame, mapOf(0 to LANGUAGE_CHANGED, 1 to ((flags and 3.inv()) or language)))
    }

    fun withOffAtNight(
        frame: ByteArray,
        value: Int,
    ): ByteArray? {
        val current = parse(frame) ?: return null
        if (value !in 0..3) return null
        if (value == current.offAtNight) return frame.copyOf()
        val flags = Frame.u8(frame, 6) ?: return null
        return Frame.withBytes(frame, mapOf(0 to NIGHT_CHANGED, 6 to ((flags and 6.inv()) or (value shl 1))))
    }

    /** What the watch returns after the write: the same value with the command byte it reports on a read. */
    fun expectedBack(
        before: ByteArray,
        written: ByteArray,
    ): ByteArray = written.copyOf().also { if (before.size > 1) it[1] = before[1] }

    val LANGUAGE_BYTES: Set<Int> = setOf(1, 2)
    val NIGHT_BYTES: Set<Int> = setOf(1, 7)
}
